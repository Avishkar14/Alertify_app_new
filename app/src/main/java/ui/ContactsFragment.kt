package ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import viewmodel.ContactsViewModel
import com.example.emergencydropalert.R
import kotlinx.coroutines.launch
import ui.ContactsAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.emergencydropalert.databinding.FragmentsContactBinding
import com.google.i18n.phonenumbers.PhoneNumberUtil
import data.AppDatabase
import data.EmergencyContact
import viewmodel.ContactsViewModelFactory


class ContactsFragment : Fragment() {

    private lateinit var viewModel: ContactsViewModel
    private lateinit var adapter: ContactsAdapter
    private lateinit var binding: FragmentsContactBinding
    override fun onCreateView(

        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentsContactBinding.inflate( inflater , container , false)
        return binding.root
        //return inflater.inflate(R.layout.fragments_contact, container, false)
    }
    private fun showAddContactDialog( existingContact : EmergencyContact ? = null) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (existingContact == null) "Add Contact" else "Edit Contact")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_contact, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val phoneInput = view.findViewById<EditText>(R.id.phoneInput)

        existingContact?.let{
            nameInput.setText(it.name)
            phoneInput.setText(it.phone)
        }

        builder.setView(view)

        builder.setPositiveButton("Save") { _, _ ->
            val name = nameInput.text.toString()
            val phone = phoneInput.text.toString()
            if (name.isNotBlank() && phone.isNotBlank()) {
                if( existingContact == null ){
                    viewModel.addContact(name, phone)
                }
                else{
                    val updatedContact = EmergencyContact(id = existingContact.id, name = name, phone = phone)
                    viewModel.updateContact(updatedContact)
                }
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_contact , null)
        val etName = dialogView.findViewById<EditText>(R.id.nameInput)
        val etPhone = dialogView.findViewById<EditText>(R.id.phoneInput)
        val spinner = dialogView.findViewById<Spinner>(R.id.country_code)

        val countryList = listOf(
            "India (+91)", "United States (+1)", "United Kingdom (+44)",
            "Canada (+1)", "Germany (+49)", "France (+33)"
        )
        val countryCodeMap = mapOf(
            "India (+91)" to "IN", "United States (+1)" to "US", "United Kingdom (+44)" to "GB",
            "Canada (+1)" to "CA", "Germany (+49)" to "DE", "France (+33)" to "FR"
        )

        val spinnerAdapter = ArrayAdapter(requireContext() , android.R.layout.simple_spinner_item , countryList)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        AlertDialog.Builder (requireContext())
            .setTitle("Add Contact")
            .setView(dialogView)
            .setPositiveButton("Save"){_,_->
                val name=etName.text.toString().trim()
                val rawphone = etPhone.text.toString().trim()
                val selectedCountry = spinner.selectedItem.toString()
                val regionCode = countryCodeMap[selectedCountry] ?: "IN"

                try {
                    val phoneUtil = PhoneNumberUtil.getInstance()
                    val numberProto = phoneUtil.parse( rawphone , regionCode )

                    if( phoneUtil.isValidNumber(numberProto)){
                        val formattedNumber = phoneUtil.format( numberProto , com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164)
                        viewModel.addContact( name , formattedNumber )
                        Toast.makeText( requireContext() , "Contact Saved." , Toast.LENGTH_SHORT).show()
                    }
                    else{
                        Toast.makeText( requireContext() , "Invalid Phone number." , Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception){
                    Toast.makeText( requireContext() , " Error : ${e.message}" , Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton( " Cancel " , null).show()

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = binding.contactsRecyclerView

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ContactsAdapter(
            emptyList(),
            onItemClick = {},
            onEdit = { contact -> showAddContactDialog(contact) },   // same as before
            onDelete = { contact -> viewModel.deleteContact(contact) }  // new functionality
        )

        recyclerView.adapter = adapter

        super.onViewCreated(view, savedInstanceState)
        //viewModel = ViewModelProvider(this)[ContactsViewModel::class.java]
        val dao = AppDatabase.getDatabase(requireContext()).emergencyContactDao()
        val viewModelFactory = ContactsViewModelFactory(dao)
        viewModel = ViewModelProvider(this, viewModelFactory).get(ContactsViewModel::class.java)

        lifecycleScope.launch {
            viewModel.allContacts.collect { contacts ->
                adapter.updateContacts(contacts)
            }
        }
            binding.addContactButton.setOnClickListener {
            showAddContactDialog()
        }

    }
}
