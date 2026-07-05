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

        val btnAccounts = findViewById<Button>(R.id.btnAccounts)
        btnAccounts.setOnClickListener {
            val accountIds = AccountManager.getAccountIds()
            val options = accountIds.map { "Account $it ${if (it == AccountManager.activeAccountId) "(Active)" else ""}" }.toMutableList()
            options.add("➕ Add Account")
            options.add("🚪 Logout Current Account")
            
            AlertDialog.Builder(this)
                .setTitle("Accounts")
                .setItems(options.toTypedArray()) { _, which ->
                    when {
                        which < accountIds.size -> {
                            val selectedId = accountIds[which]
                            if (selectedId != AccountManager.activeAccountId) {
                                TdClient.switchAccount(selectedId)
                            }
                        }
                        which == accountIds.size -> { // Add Account
                            val newId = AccountManager.createNewAccount()
                            TdClient.switchAccount(newId)
                        }
                        which == accountIds.size + 1 -> { // Logout
                            AlertDialog.Builder(this)
                                .setTitle("Logout")
                                .setMessage("Are you sure you want to logout?")
                                .setPositiveButton("Logout") { _, _ -> TdClient.logout() }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
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
            val imgAvatar: android.widget.ImageView = view.findViewById(R.id.imgAvatar)
            val imgPinned: android.widget.ImageView = view.findViewById(R.id.imgPinned)
            val txtUnreadCount: TextView = view.findViewById(R.id.txtUnreadCount)
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

            // Unread Count
            if (chat.unreadCount > 0) {
                holder.txtUnreadCount.visibility = View.VISIBLE
                holder.txtUnreadCount.text = chat.unreadCount.toString()
            } else {
                holder.txtUnreadCount.visibility = View.GONE
            }

            // Pinned Icon
            val isPinned = chat.positions?.any { it.list?.constructor == TdApi.ChatListMain.CONSTRUCTOR && it.isPinned } ?: false
            holder.imgPinned.visibility = if (isPinned) View.VISIBLE else View.GONE

            // Avatar via Glide
            holder.imgAvatar.setImageResource(R.drawable.bg_avatar_placeholder) // reset first
            val photo = chat.photo?.small
            if (photo != null) {
                if (photo.local?.isDownloadingCompleted == true) {
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(photo.local?.path)
                        .circleCrop()
                        .into(holder.imgAvatar)
                } else if (photo.local?.isDownloadingActive == false && photo.local?.canBeDownloaded == true) {
                    // Start download
                    TdClient.currentAccount.client?.send(TdApi.DownloadFile(photo.id, 1, 0, 0, true)) {}
                }
            }

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
