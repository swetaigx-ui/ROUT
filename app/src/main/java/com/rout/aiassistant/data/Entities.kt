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

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,          // normalized phrase the user says, e.g. "good morning"
    val steps: String,            // one command per line, run through the command router
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
