package com.thatgfsj.mypackage.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    @Query("SELECT * FROM stations ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: Long): StationEntity?

    @Query("SELECT MAX(sortOrder) FROM stations")
    suspend fun maxSortOrder(): Int?

    @Insert
    suspend fun insert(station: StationEntity): Long

    @Update
    suspend fun update(station: StationEntity)

    @Delete
    suspend fun delete(station: StationEntity)
}
