package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.BlockReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockReportDao {
    @Query("SELECT * FROM blocks_reports WHERE blockerId = :userId AND isBlocked = 1")
    fun getBlockedUsers(userId: String): Flow<List<BlockReportEntity>>

    @Query("SELECT * FROM blocks_reports WHERE blockerId = :blockerId AND targetId = :targetId LIMIT 1")
    suspend fun getBlockReportSync(blockerId: String, targetId: String): BlockReportEntity?

    @Query("SELECT * FROM blocks_reports WHERE blockerId = :blockerId AND targetId = :targetId LIMIT 1")
    fun getBlockReport(blockerId: String, targetId: String): Flow<BlockReportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockReport(blockReport: BlockReportEntity)

    @Query("DELETE FROM blocks_reports WHERE blockerId = :blockerId AND targetId = :targetId")
    suspend fun unblockUser(blockerId: String, targetId: String)
}
