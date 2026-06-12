package com.aizwal.pandanclone.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE startTime >= :startOfDay ORDER BY startTime DESC")
    fun getSessionsForDay(startOfDay: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime >= :startDate ORDER BY startTime ASC")
    fun getSessionsFromDate(startDate: Long): Flow<List<SessionEntity>>
}
