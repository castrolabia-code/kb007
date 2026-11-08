package com.kb007.recorder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.TriggerType

class Converters {
    @TypeConverter fun fmtToStr(v: AudioFormatType) = v.name
    @TypeConverter fun strToFmt(v: String) = AudioFormatType.valueOf(v)
    @TypeConverter fun trgToStr(v: TriggerType) = v.name
    @TypeConverter fun strToTrg(v: String) = TriggerType.valueOf(v)
    @TypeConverter fun stToStr(v: RecordingStatus) = v.name
    @TypeConverter fun strToSt(v: String) = RecordingStatus.valueOf(v)
}

@Database(entities = [Recording::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class KbDatabase : RoomDatabase() {
    abstract fun recordings(): RecordingDao

    companion object {
        fun create(context: Context): KbDatabase =
            Room.databaseBuilder(context, KbDatabase::class.java, "kb007.db").build()
    }
}
