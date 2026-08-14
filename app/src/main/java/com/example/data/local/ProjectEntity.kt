package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val device: String,
    val androidVersion: String,
    val architecture: String,
    val trebleStatus: String,
    val baseRom: String,
    val notes: String
)
