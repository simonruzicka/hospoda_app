package com.example.h1.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pubs")
data class Pub(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val imageUri: String?
)

@Entity(tableName = "drinks")
data class Drink(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pubId: Int,
    val name: String,
    val price: Int,
    val icon: String = "🍺"
)

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

// NOVÉ: Záznam o celém večeru (Sezení)
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pubId: Int,
    val dateMillis: Long, // Datum a čas uložení
    val totalSpent: Int
)

// NOVÉ: Konkrétní vypitý drink přiřazený člověku a sezení
@Entity(tableName = "consumptions")
data class Consumption(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val personId: Int,
    val drinkName: String,
    val drinkIcon: String,
    val price: Int
)