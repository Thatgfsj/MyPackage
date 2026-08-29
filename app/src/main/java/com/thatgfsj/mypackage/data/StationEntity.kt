package com.thatgfsj.mypackage.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stations", indices = [Index("sortOrder")])
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: String = Platform.PDD.name,
    val rawLink: String = "",
    val lockerRange: String = "",
    val imagePath: String = "",
    val customPackage: String = "",
    val sortOrder: Int = 0
)
