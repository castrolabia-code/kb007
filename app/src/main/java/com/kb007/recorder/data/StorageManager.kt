package com.kb007.recorder.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kb007.recorder.data.prefs.StorageTarget
import com.kb007.recorder.data.prefs.TriggerType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dónde viven los archivos:
 *  - Mientras se graba: siempre en almacenamiento interno de la app ([workDir]), así el
 *    archivo de trabajo es nuestro y se puede reparar tras un corte.
 *  - Al terminar: se publica en el destino elegido (carpeta de la app, Música pública o SAF).
 */
class StorageManager(private val context: Context) {

    val workDir: File = File(context.filesDir, "en_curso").apply { mkdirs() }

    /** Carpeta privada de la app (visible en Android/data/... con un explorador de archivos). */
    val appDir: File = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "KB-007") }
        ?: File(context.filesDir, "grabaciones")).apply { mkdirs() }

    /** Bytes libres donde se graba. */
    fun freeBytes(): Long = runCatching { StatFs(workDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** Arma el nombre a partir del patrón ({fecha} {hora} {modo} {n}). Sin extensión. */
    fun buildName(pattern: String, trigger: TriggerType, seq: Int, now: Date = Date()): String =
        formatName(pattern, trigger, seq, now)

    data class Published(val uri: Uri, val sizeBytes: Long)

    /** Mueve/copia el archivo final a su destino. Si el destino falla, cae a la carpeta de la app. */
    fun publish(file: File, displayName: String, mime: String, target: StorageTarget, treeUri: String?): Published {
        val ext = file.extension
        val name = "$displayName.$ext"
        try {
            when (target) {
                StorageTarget.PUBLIC_MUSIC -> publishToMusic(file, name, mime)?.let { return it }
                StorageTarget.CUSTOM_TREE -> treeUri?.let { publishToTree(file, name, mime, Uri.parse(it)) }?.let { return it }
                StorageTarget.APP_PRIVATE -> Unit
            }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo publicar en $target, se guarda en la carpeta de la app", t)
        }
        val dest = uniqueFile(appDir, displayName, ext)
        if (!file.renameTo(dest)) { file.copyTo(dest, overwrite = true); file.delete() }
        return Published(Uri.fromFile(dest), dest.length())
    }

    private fun publishToMusic(file: File, name: String, mime: String): Published? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/KB-007")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val size = file.length(); file.delete()
            return Published(uri, size)
        }
        // Android 8/9: escritura directa con permiso de almacenamiento
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return null
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "KB-007").apply { mkdirs() }
        val dest = uniqueFile(dir, name.substringBeforeLast('.'), file.extension)
        file.copyTo(dest); file.delete()
        return Published(Uri.fromFile(dest), dest.length())
    }

    private fun publishToTree(file: File, name: String, mime: String, tree: Uri): Published? {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return null
        resolver.openOutputStream(doc)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        val size = file.length(); file.delete()
        return Published(doc, size)
    }

    fun delete(uri: Uri): Boolean = runCatching {
        when {
            uri.scheme == "file" -> File(uri.path!!).delete()
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)

    /** Renombra el archivo físico (mejor esfuerzo). Devuelve la nueva uri o la misma si no pudo. */
    fun rename(uri: Uri, newBaseName: String): Uri = runCatching {
        when {
            uri.scheme == "file" -> {
                val f = File(uri.path!!)
                val dest = uniqueFile(f.parentFile!!, sanitize(newBaseName), f.extension)
                if (f.renameTo(dest)) Uri.fromFile(dest) else uri
            }
            DocumentsContract.isDocumentUri(context, uri) -> {
                val ext = displayName(uri)?.substringAfterLast('.', "") ?: ""
                DocumentsContract.renameDocument(context.contentResolver, uri, "${sanitize(newBaseName)}.$ext") ?: uri
            }
            else -> {
                val ext = displayName(uri)?.substringAfterLast('.', "") ?: ""
                val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "${sanitize(newBaseName)}.$ext") }
                context.contentResolver.update(uri, v, null, null); uri
            }
        }
    }.getOrDefault(uri)

    fun exists(uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") File(uri.path!!).exists()
        else context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** Uri apta para compartir con otras apps (FileProvider para archivos propios). */
    fun shareableUri(uri: Uri): Uri =
        if (uri.scheme == "file") FileProvider.getUriForFile(context, "${context.packageName}.files", File(uri.path!!)) else uri

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var f = File(dir, "$base.$ext")
        var i = 2
        while (f.exists()) f = File(dir, "$base ($i).$ext").also { i++ }
        return f
    }

    companion object {
        private const val TAG = "KB007/Storage"

        fun sanitize(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().ifEmpty { "grabacion" }.take(120)

        fun formatName(pattern: String, trigger: TriggerType, seq: Int, now: Date): String {
            val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            val hora = SimpleDateFormat("HH-mm-ss", Locale.US).format(now)
            val modo = if (trigger == TriggerType.KEYWORD) "clave" else "manual"
            return sanitize(
                pattern.replace("{fecha}", fecha).replace("{hora}", hora)
                    .replace("{modo}", modo).replace("{n}", seq.toString().padStart(3, '0'))
            )
        }
    }
}
