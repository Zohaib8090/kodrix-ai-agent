package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "project_chat_sessions",
    indices = [Index(value = ["projectId"])]
)
data class ProjectChatSession(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_chat_messages",
    indices = [Index(value = ["sessionId"]), Index(value = ["projectId"])]
)
data class ProjectChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val projectId: String,
    val sender: String, // "USER" or "AI"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ProjectChatDao {

    @Query("SELECT * FROM project_chat_sessions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getSessionsForProject(projectId: String): Flow<List<ProjectChatSession>>

    @Query("SELECT * FROM project_chat_sessions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    suspend fun getDirectSessionsForProject(projectId: String): List<ProjectChatSession>

    @Query("SELECT * FROM project_chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ProjectChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ProjectChatSession)

    @Update
    suspend fun updateSession(session: ProjectChatSession)

    @Query("DELETE FROM project_chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("SELECT * FROM project_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ProjectChatMessage>>

    @Query("SELECT * FROM project_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDirectMessagesForSession(sessionId: String): List<ProjectChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ProjectChatMessage)

    @Query("DELETE FROM project_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM project_chat_messages WHERE projectId = :projectId")
    suspend fun deleteMessagesForProject(projectId: String)

    @Query("DELETE FROM project_chat_sessions WHERE projectId = :projectId")
    suspend fun deleteSessionsForProject(projectId: String)
}
