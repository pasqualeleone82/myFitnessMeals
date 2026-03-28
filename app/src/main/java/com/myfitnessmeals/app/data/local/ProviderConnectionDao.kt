package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProviderConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: ProviderConnectionEntity)

    @Query("SELECT * FROM provider_connection WHERE provider = :provider")
    suspend fun getByProvider(provider: String): ProviderConnectionEntity?
}
