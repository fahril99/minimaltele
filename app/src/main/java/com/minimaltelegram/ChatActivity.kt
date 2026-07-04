package com.minimaltelegram

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private var chatId: Long = 0L
    private val messages = mutableListOf<TdApi.Message>()
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var editMessage: EditText
    private val adapter = MessageAdapter()

    // File pickers
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendFileFromUri(it, "photo") }
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendFileFromUri(it, "video") }
    }

    private val pickDocument = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendFileFromUri(it, "document") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatId = intent.getLongExtra("chat_id", 0L)
        val chatTitle = intent.getStringExtra("chat_title") ?: "Chat"

        val txtChatTitle = findViewById<TextView>(R.id.txtChatTitle)
        txtChatTitle.text = chatTitle

        editMessage = findViewById(R.id.editMessage)
        recyclerMessages = findViewById(R.id.recyclerMessages)

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerMessages.layoutManager = layoutManager
        recyclerMessages.adapter = adapter

        // Send text
        val btnSend = findViewById<Button>(R.id.btnSend)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                TdClient.sendTextMessage(chatId, text)
                editMessage.text.clear()
            }
        }

        // Attachment
        val btnAttach = findViewById<Button>(R.id.btnAttach)
        btnAttach.setOnClickListener {
            showAttachmentPicker()
        }

        // Listen for new messages
        TdClient.onNewMessage = { message ->
            if (message.chatId == chatId) {
                messages.add(message)
                adapter.notifyItemInserted(messages.size - 1)
                recyclerMessages.scrollToPosition(messages.size - 1)
            }
        }

        TdClient.onError = { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }

        // Mark chat as read
        TdClient.openChat(chatId)

        // Load history
        loadHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        TdClient.closeChat(chatId)
    }

    private fun loadHistory() {
        TdClient.getChatHistory(chatId, 0L, 50) { history ->
            messages.clear()
            messages.addAll(history.reversed()) // TDLib returns newest first, we want oldest first
            adapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) {
                recyclerMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun showAttachmentPicker() {
        val options = arrayOf("Photo", "Video", "Document")
        AlertDialog.Builder(this)
            .setTitle("Send attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickPhoto.launch("image/*")
                    1 -> pickVideo.launch("video/*")
                    2 -> pickDocument.launch("*/*")
                }
            }
            .show()
    }

    /**
     * Copy file from content URI to cache dir (TDLib needs a real file path)
     * then send via TDLib.
     */
    private fun sendFileFromUri(uri: Uri, type: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val extension = getExtension(uri) ?: "tmp"
            val tempFile = File(cacheDir, "send_${System.currentTimeMillis()}.$extension")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()

            val path = tempFile.absolutePath
            when (type) {
                "photo" -> TdClient.sendPhoto(chatId, path)
                "video" -> TdClient.sendVideo(chatId, path)
                "document" -> TdClient.sendDocument(chatId, path)
            }

            Toast.makeText(this, "Sending $type...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getExtension(uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val mime = contentResolver.getType(uri)
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        } else {
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        }
    }

    // ---- Message Adapter ----

    inner class MessageAdapter : RecyclerView.Adapter<MessageAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val container: LinearLayout = view.findViewById(R.id.messageContainer)
            val txtSender: TextView = view.findViewById(R.id.txtSender)
            val txtContent: TextView = view.findViewById(R.id.txtContent)
            val txtTime: TextView = view.findViewById(R.id.txtTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = messages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = messages[position]
            val isOutgoing = msg.isOutgoing

            // Sender
            if (isOutgoing) {
                holder.txtSender.text = "You"
                holder.txtSender.setTextColor(0xFF4CAF50.toInt())
            } else {
                val senderId = msg.senderId
                val senderName = when (senderId?.constructor) {
                    TdApi.MessageSenderUser.CONSTRUCTOR -> {
                        val userId = (senderId as TdApi.MessageSenderUser).userId
                        if (userId == TdClient.myUserId) "You" else "User $userId"
                    }
                    TdApi.MessageSenderChat.CONSTRUCTOR -> {
                        val senderChatId = (senderId as TdApi.MessageSenderChat).chatId
                        TdClient.chats[senderChatId]?.title ?: "Chat $senderChatId"
                    }
                    else -> "Unknown"
                }
                holder.txtSender.text = senderName
                holder.txtSender.setTextColor(0xFF0088CC.toInt())
            }

            // Content
            holder.txtContent.text = TdClient.getMessageText(msg.content)

            // Time
            holder.txtTime.text = formatTime(msg.date)

            // Alignment
            val gravity = if (isOutgoing) Gravity.END else Gravity.START
            holder.container.gravity = gravity
        }
    }

    private fun formatTime(timestamp: Int): String {
        val date = Date(timestamp.toLong() * 1000)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(date)
    }
}
