package com.europartes.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EuropartesDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}
