package com.myfitnessmeals.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_connection")
data class ProviderConnectionEntity(
    @PrimaryKey
    val provider: String,
    @ColumnInfo(name = "connection_state")
    val connectionState: String,
    @ColumnInfo(name = "token_ref")
    val tokenRef: String?,
    val scopes: String,
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long?,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
