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

    @Query("SELECT * FROM provider_connection ORDER BY provider ASC")
    suspend fun getAll(): List<ProviderConnectionEntity>

    @Query("DELETE FROM provider_connection")
    suspend fun deleteAll(): Int
}
