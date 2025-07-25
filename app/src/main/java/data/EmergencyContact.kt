package data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class EmergencyContact(
    @PrimaryKey( autoGenerate = true)
    val id : Int = 0 ,
    val name: String ,
    val phone : String
)