package io.brokentooth.soma.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.brokentooth.soma.data.model.Message
import io.brokentooth.soma.data.model.Session

@Database(
    entities = [Session::class, Message::class],
    version = 1,
    exportSchema = false
)
abstract class SomaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}
