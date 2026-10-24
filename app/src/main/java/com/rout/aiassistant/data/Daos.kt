package com.rout.aiassistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun conversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY ts ASC")
    fun messages(cid: Long): Flow<List<Message>>

    @Insert
    suspend fun insertConversation(c: Conversation): Long

    @Insert
    suspend fun insertMessage(m: Message): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteMessages(cid: Long)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("SELECT text FROM messages WHERE role = 'user' AND text LIKE '%' || :q || '%' ORDER BY ts DESC LIMIT 20")
    suspend fun searchUserMessages(q: String): List<String>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun notes(): Flow<List<Note>>

    @Insert
    suspend fun insert(n: Note): Long

    @Update
    suspend fun update(n: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    suspend fun clear()

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT 50")
    suspend fun latest(): List<Note>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, dueAt IS NULL, dueAt ASC")
    fun tasks(): Flow<List<TaskItem>>

    @Insert
    suspend fun insert(t: TaskItem): Long

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun clear()

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskItem?
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_facts ORDER BY savedAt DESC")
    fun facts(): Flow<List<MemoryFact>>

    @Insert
    suspend fun insert(f: MemoryFact): Long

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clear()

    @Query("SELECT * FROM memory_facts ORDER BY savedAt DESC")
    suspend fun all(): List<MemoryFact>
}
