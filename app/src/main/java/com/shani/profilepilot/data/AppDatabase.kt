package com.shani.profilepilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Standard pattern: converter listed in @TypeConverters, Room creates it.
// (addTypeConverter + @ProvidedTypeConverter crashed Room init — see v1.2 report.)
@Database(
    entities = [Account::class, Folder::class, ProxyProfile::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun proxyDao(): ProxyDao

    companion object {
        @Volatile
        private var I: AppDatabase? = null

        // v1.5: killSwitch column (default ON = pehle wala strict behavior barkarar)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN killSwitch INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun get(c: Context): AppDatabase {
            return I ?: synchronized(this) {
                I ?: Room.databaseBuilder(
                    c.applicationContext,
                    AppDatabase::class.java,
                    "profilepilot.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { I = it }
            }
        }
    }
}


