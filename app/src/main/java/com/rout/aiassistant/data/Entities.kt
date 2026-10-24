package com.rout.aiassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" | "rout" | "system"
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long? = null,
    val done: Boolean = false
)

@Entity(tableName = "memory_facts")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val savedAt: Long = System.currentTimeMillis()
)
