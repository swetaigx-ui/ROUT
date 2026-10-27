package com.rout.aiassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Conversation::class, Message::class, Note::class, TaskItem::class, MemoryFact::class, Routine::class],
    version = 2,
    exportSchema = false
)
abstract class RoutDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun notes(): NoteDao
    abstract fun tasks(): TaskDao
    abstract fun memory(): MemoryDao
    abstract fun routines(): RoutineDao

    companion object {
        @Volatile private var instance: RoutDatabase? = null
        /** Unit tests swap in an in-memory DB before building a ViewModel. */
        @Volatile var testInstance: RoutDatabase? = null

        fun get(context: Context): RoutDatabase =
            testInstance ?: instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, RoutDatabase::class.java, "rout.db")
                    .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trigger` TEXT NOT NULL, `steps` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL)")
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
