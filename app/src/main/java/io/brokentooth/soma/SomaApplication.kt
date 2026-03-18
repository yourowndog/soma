package io.brokentooth.soma

import android.app.Application
import androidx.room.Room
import io.brokentooth.soma.data.db.SomaDatabase

class SomaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            SomaDatabase::class.java,
            "soma.db"
        ).build()
    }

    companion object {
        /** Singleton database handle. Guaranteed non-null after Application.onCreate(). */
        lateinit var database: SomaDatabase
            private set
    }
}
