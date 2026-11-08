package com.kb007.recorder.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kb007.recorder.audio.encode.Transcoder
import com.kb007.recorder.data.StorageManager
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.prefs.AudioFormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Compartir grabaciones (WhatsApp directo o selector del sistema) con FileProvider. */
class ShareHelper(private val context: Context, private val storage: StorageManager) {

    /** Paquete de WhatsApp instalado (normal o Business), o null. */
    fun whatsAppPackage(): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    /** WhatsApp maneja mal WAV (pesados y sin vista previa): conviene convertir. */
    fun shouldOfferConversion(r: Recording): Boolean = r.format == AudioFormatType.WAV_PCM

    fun mimeFor(r: Recording): String = when (r.format) {
        AudioFormatType.AAC_M4A -> "audio/mp4"
        AudioFormatType.OPUS_OGG -> "audio/ogg"
        AudioFormatType.WAV_PCM -> "audio/wav"
    }

    private fun sendIntent(uri: Uri, mime: String, title: String) = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri(title, uri)
    }

    /** "Compartir con…": selector del sistema. */
    fun shareWithChooser(r: Recording, overrideUri: Uri? = null, overrideMime: String? = null) {
        val uri = overrideUri ?: storage.shareableUri(Uri.parse(r.uri))
        val chooser = Intent.createChooser(sendIntent(uri, overrideMime ?: mimeFor(r), r.title), "Compartir «${r.title}»")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Abre WhatsApp directamente con el audio adjunto. Devuelve false si no está instalado. */
    fun shareToWhatsApp(r: Recording, overrideUri: Uri? = null, overrideMime: String? = null): Boolean {
        val pkg = whatsAppPackage() ?: return false
        val uri = overrideUri ?: storage.shareableUri(Uri.parse(r.uri))
        val i = sendIntent(uri, overrideMime ?: mimeFor(r), r.title).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try { context.startActivity(i); true } catch (e: ActivityNotFoundException) { false }
    }

    /** Convierte un WAV a M4A en caché y devuelve la uri compartible. */
    suspend fun convertForSharing(r: Recording, progress: (Float) -> Unit): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "compartir").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val input = context.contentResolver.openInputStream(Uri.parse(r.uri)) ?: error("No se pudo abrir el archivo")
        val out = Transcoder.wavToM4a(input, dir, StorageManager.sanitize(r.title), 128, progress)
        FileProvider.getUriForFile(context, "${context.packageName}.files", out)
    }
}
