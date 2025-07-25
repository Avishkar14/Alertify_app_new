package data

//import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao{
    @Query("Select * from contacts")
    fun getAll(): Flow<List<EmergencyContact>>

    @Insert
    suspend fun insert(contact: EmergencyContact)

    @Update
    suspend fun update(contact: EmergencyContact)
    @Delete
    suspend fun delete(contact: EmergencyContact)
}