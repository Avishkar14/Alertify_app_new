package ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import data.AppDatabase
import viewmodel.ContactsViewModelFactory


class ContactsFragment : Fragment() {

    private lateinit var viewModel: ContactsViewModel
    private lateinit var adapter: ContactsAdapter

    override fun onCreateView(

        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragments_contact, container, false)
    }
    private fun showAddContactDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add Contact")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_contact, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val phoneInput = view.findViewById<EditText>(R.id.phoneInput)

        builder.setView(view)

        builder.setPositiveButton("Add") { _, _ ->
            val name = nameInput.text.toString()
            val phone = phoneInput.text.toString()
            if (name.isNotBlank() && phone.isNotBlank()) {
                viewModel.addContact(name, phone)
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.contactsRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ContactsAdapter(emptyList()) // initially empty
        recyclerView.adapter = adapter

        //viewModel = ViewModelProvider(this)[ContactsViewModel::class.java]
        val dao = AppDatabase.getDatabase(requireContext()).emergencyContactDao()
        val factory = ContactsViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[ContactsViewModel::class.java]

        lifecycleScope.launch {
            viewModel.allContacts.collect { contacts ->
                adapter.updateContacts(contacts)
            }
        }
        val addContactButton = view.findViewById<Button>(R.id.addContactButton)
        addContactButton.setOnClickListener {
            showAddContactDialog()
        }

    }
}
