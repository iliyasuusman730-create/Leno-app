package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.CallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls WHERE callerId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    fun getCallsForUser(userId: String): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)

    @Query("DELETE FROM calls WHERE callId = :callId")
    suspend fun deleteCall(callId: String)

    @Query("DELETE FROM calls WHERE callerId = :userId OR receiverId = :userId")
    suspend fun clearCallsForUser(userId: String)
}
