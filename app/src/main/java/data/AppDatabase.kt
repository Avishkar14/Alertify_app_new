package data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities  = [EmergencyContact::class] , version=1 )

abstract class AppDatabase : RoomDatabase(){
    abstract fun emergencyContactDao() : EmergencyContactDao

    companion object{
        @Volatile private var INSTANCE : AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this){
                INSTANCE ?: Room.databaseBuilder( context.applicationContext , AppDatabase::class.java , "emergency_contact_db" ).build().also { INSTANCE = it }
            }
        }
    }
}