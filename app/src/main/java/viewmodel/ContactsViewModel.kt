package viewmodel

import data.EmergencyContact
import data.EmergencyContactDao
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ContactsViewModel( private val dao: EmergencyContactDao ) :ViewModel(){

    val allContacts : Flow<List<EmergencyContact>> = dao.getAll()

//    fun addContact( name : String , phone : String ){
//        val newContact = EmergencyContact ( name = name , phone = phone)
//        viewModelScope.launch {
//            dao.insert(newContact)
//        }
//    }
    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            val contact = EmergencyContact(name = name, phone = phone)
            dao.insert(contact)
        }
    }

    fun updateContact( contact : EmergencyContact){
        viewModelScope.launch{
            dao.update(contact)
        }
    }

    fun deleteContact(contact : EmergencyContact){
        viewModelScope.launch{
            dao.delete(contact)
        }
    }
}

class EmergencyContactViewModelFactory( private val dao: EmergencyContactDao ) :
    ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T> ): T{
            if ( modelClass.isAssignableFrom( ContactsViewModel::class.java )){
                @Suppress("UNCHECKED_CAST")
                return ContactsViewModel(dao) as T
            }
            throw IllegalArgumentException ( "Unknown ViewModel class" )
    }
}