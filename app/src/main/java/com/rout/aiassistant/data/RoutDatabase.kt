package com.rout.aiassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Conversation::class, Message::class, Note::class, TaskItem::class, MemoryFact::class],
    version = 1,
    exportSchema = false
)
abstract class RoutDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun notes(): NoteDao
    abstract fun tasks(): TaskDao
    abstract fun memory(): MemoryDao

    companion object {
        @Volatile private var instance: RoutDatabase? = null
        fun get(context: Context): RoutDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, RoutDatabase::class.java, "rout.db")
                    .build().also { instance = it }
            }
    }
}
