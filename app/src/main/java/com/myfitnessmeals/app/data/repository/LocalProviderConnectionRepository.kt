package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.ProviderConnectionDao
import com.myfitnessmeals.app.data.local.ProviderConnectionEntity
import com.myfitnessmeals.app.domain.model.ProviderType

class LocalProviderConnectionRepository(
    private val providerConnectionDao: ProviderConnectionDao,
) {
    suspend fun upsertConnection(connection: ProviderConnectionEntity) {
        providerConnectionDao.upsert(connection)
    }

    suspend fun getConnection(provider: ProviderType): ProviderConnectionEntity? =
        providerConnectionDao.getByProvider(provider.name)
}
