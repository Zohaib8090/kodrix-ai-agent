package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildRecordDao {
    @Query("SELECT * FROM build_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<BuildRecord>>

    @Query("SELECT * FROM build_records WHERE id = :id LIMIT 1")
    fun getRecordById(id: String): Flow<BuildRecord?>

    @Query("SELECT * FROM build_records WHERE id = :id LIMIT 1")
    suspend fun getRecordDirect(id: String): BuildRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BuildRecord)

    @Update
    suspend fun update(record: BuildRecord)

    @Delete
    suspend fun delete(record: BuildRecord)

    @Query("DELETE FROM build_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM build_records")
    suspend fun clearAll()
}
