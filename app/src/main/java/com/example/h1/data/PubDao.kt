package com.example.h1.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PubDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPub(pub: Pub): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrinks(drinks: List<Drink>)

    @Update suspend fun updateDrink(drink: Drink)
    @Delete suspend fun deleteDrink(drink: Drink)

    @Query("SELECT * FROM pubs ORDER BY id DESC")
    fun getAllPubs(): Flow<List<Pub>>

    @Query("SELECT * FROM drinks WHERE pubId = :pubId")
    fun getDrinksForPub(pubId: Int): Flow<List<Drink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person)

    @Update suspend fun updatePerson(person: Person)
    @Delete suspend fun deletePerson(person: Person)

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :personId")
    fun getPersonById(personId: Int): Flow<Person?>

    // NOVÉ: Ukládání sezení
    @Insert
    suspend fun insertSession(session: Session): Long

    @Insert
    suspend fun insertConsumptions(consumptions: List<Consumption>)

    @Query("SELECT * FROM sessions ORDER BY dateMillis DESC")
    fun getAllSessions(): Flow<List<Session>>

    // --- FUNKCE PRO DETAIL A POKRAČOVÁNÍ SEZENÍ ---
    @Query("SELECT * FROM consumptions WHERE sessionId = :sessionId")
    fun getConsumptionsForSessionFlow(sessionId: Int): Flow<List<Consumption>>

    @Query("SELECT * FROM consumptions WHERE sessionId = :sessionId")
    suspend fun getConsumptionsList(sessionId: Int): List<Consumption>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Int): Flow<Session?>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Int)

    @Query("DELETE FROM consumptions WHERE sessionId = :sessionId")
    suspend fun deleteConsumptionsForSession(sessionId: Int)

    @Delete
    suspend fun deletePub(pub: Pub)

    @Query("DELETE FROM drinks WHERE pubId = :pubId")
    suspend fun deleteDrinksForPub(pubId: Int)

    @Query("SELECT * FROM consumptions WHERE personId = :personId")
    fun getAllConsumptionsForPerson(personId: Int): Flow<List<Consumption>>
}