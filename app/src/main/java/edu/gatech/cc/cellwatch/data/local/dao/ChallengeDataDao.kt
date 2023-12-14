package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import edu.gatech.cc.cellwatch.data.local.model.ChallengeDataEntity
import edu.gatech.cc.cellwatch.data.model.ChallengeData
import kotlinx.coroutines.flow.Flow

abstract class ChallengeDataDao {
    @Insert
    abstract suspend fun insertChallengeData(challengeData: ChallengeData)
    
    @Update
    abstract suspend fun updateChallengeData(challengeData: ChallengeData)
    
    @Delete
    abstract suspend fun deleteChallengeData(challengeData: ChallengeData)
    
    @Query("DELETE FROM ChallengeDataEntity")
    abstract suspend fun deleteAllChallengeData()

    @Query("SELECT * FROM ChallengeDataEntity WHERE id = :id")
    abstract suspend fun getChallengeDataById(id: String): ChallengeDataEntity

    @Query("SELECT * FROM ChallengeDataEntity")
    abstract suspend fun getChallengeData(): List<ChallengeDataEntity>

    @Query("SELECT * FROM ChallengeDataEntity")
    abstract fun getChallengeDataFlow(): Flow<List<ChallengeDataEntity>>
}
