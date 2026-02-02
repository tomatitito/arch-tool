package com.breuninger.entdecken.domain.repository

interface ProduktRepository {
    suspend fun save(produktDocument: ProduktDocument): Unit
    suspend fun exists(id: ProduktId): Boolean
    suspend fun deleteByIds(produktIds: List<ProduktId>): Unit
}
