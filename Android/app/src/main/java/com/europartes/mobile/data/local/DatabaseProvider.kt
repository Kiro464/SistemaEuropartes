package com.europartes.mobile.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile private var instance: EuropartesDatabase? = null

    fun get(context: Context): EuropartesDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                EuropartesDatabase::class.java,
                "europartes_local.db",
            ).build().also { instance = it }
        }
}
