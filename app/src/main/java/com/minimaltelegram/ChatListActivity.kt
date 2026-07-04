package com.minimaltelegram

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListActivity : AppCompatActivity() {

    private lateinit var recyclerChats: RecyclerView
    private val adapter = ChatListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        recyclerChats = findViewById(R.id.recyclerChats)
        recyclerChats.layoutManager = LinearLayoutManager(this)
        recyclerChats.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        recyclerChats.adapter = adapter

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    TdClient.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        TdClient.onChatsUpdated = {
            adapter.notifyDataSetChanged()
        }

        TdClient.onAuthStateChanged = { state ->
            when (state.constructor) {
                TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR,
                TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }

        TdClient.onError = { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }

        // Load chats
        TdClient.loadChats()
    }

    override fun onResume() {
        super.onResume()
        TdClient.onChatsUpdated = {
            adapter.notifyDataSetChanged()
        }
        TdClient.onAuthStateChanged = { state ->
            when (state.constructor) {
                TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR,
                TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    // ---- Adapter ----

    inner class ChatListAdapter : RecyclerView.Adapter<ChatListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val txtTitle: TextView = view.findViewById(R.id.txtChatTitle)
            val txtTime: TextView = view.findViewById(R.id.txtTime)
            val txtLastMsg: TextView = view.findViewById(R.id.txtLastMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = TdClient.orderedChatIds.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val chatId = TdClient.orderedChatIds.getOrNull(position) ?: return
            val chat = TdClient.chats[chatId] ?: return

            holder.txtTitle.text = chat.title ?: "Unknown"

            val lastMsg = chat.lastMessage
            if (lastMsg != null) {
                holder.txtLastMsg.text = TdClient.getMessageText(lastMsg.content)
                holder.txtTime.text = formatTime(lastMsg.date)
            } else {
                holder.txtLastMsg.text = ""
                holder.txtTime.text = ""
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@ChatListActivity, ChatActivity::class.java)
                intent.putExtra("chat_id", chatId)
                intent.putExtra("chat_title", chat.title ?: "Chat")
                startActivity(intent)
            }
        }
    }

    private fun formatTime(timestamp: Int): String {
        val date = Date(timestamp.toLong() * 1000)
        val now = Date()
        val sdfDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        return if (sdfDate.format(date) == sdfDate.format(now)) {
            sdfTime.format(date)
        } else {
            sdfDate.format(date)
        }
    }
}
