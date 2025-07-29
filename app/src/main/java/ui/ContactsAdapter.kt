package ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.emergencydropalert.R
import data.EmergencyContact

class ContactsAdapter(
    private var contacts: List<EmergencyContact>,
    private val onItemClick: (EmergencyContact) -> Unit ,
    private val onEdit: (EmergencyContact) -> Unit ,
    private val onDelete: (EmergencyContact) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind( contact : EmergencyContact){
            itemView.findViewById<TextView>(R.id.nameText).text = contact.name
            itemView.findViewById<TextView>(R.id.phoneText).text = contact.phone

            itemView.setOnClickListener{
                onItemClick(contact)
            }
        }
//        val nameText: TextView = itemView.findViewById(R.id.nameText)
//        val phoneText: TextView = itemView.findViewById(R.id.phoneText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun getItemCount(): Int = contacts.size

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        val nameText = holder.itemView.findViewById<TextView>(R.id.nameText)
        val phoneText = holder.itemView.findViewById<TextView>(R.id.phoneText)
//        holder.bind(contacts[position])
        nameText.text = contact.name
        phoneText.text = contact.phone

        holder.itemView.setOnClickListener{
            onEdit(contact)
        }

        holder.itemView.setOnLongClickListener{
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Delete contact")
                .setMessage("Are you sure you want to delete ${contact.name}?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete"){_,_->
                    onDelete(contact)
                }

                .show()
            true
        }

    }

    // 🔁 This lets you update the list when data changes
    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
}
