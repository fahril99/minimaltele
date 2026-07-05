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
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerMessages: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var txtChatTitle: TextView
    private lateinit var layoutReplyPreview: View
    private lateinit var txtReplyName: TextView
    private lateinit var txtReplyText: TextView
    private lateinit var btnCloseReply: ImageView

    private val messages = mutableListOf<TdApi.Message>()
    private val adapter = MessageAdapter()

    private var chatId: Long = 0
    private var replyToMessageId: Long = 0L
    private var editMessageId: Long = 0L

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
        layoutReplyPreview = findViewById(R.id.layoutReplyPreview)
        txtReplyName = findViewById(R.id.txtReplyName)
        txtReplyText = findViewById(R.id.txtReplyText)
        btnCloseReply = findViewById(R.id.btnCloseReply)
        
        btnCloseReply.setOnClickListener {
            cancelReplyOrEdit()
        }

        val btnSend = findViewById<Button>(R.id.btnSend)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                if (editMessageId != 0L) {
                    TdClient.editMessageText(chatId, editMessageId, text)
                } else {
                    TdClient.sendTextMessage(chatId, text, replyToMessageId)
                }
                editMessage.text.clear()
                cancelReplyOrEdit()
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
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_attachment, null)
        dialog.setContentView(view)
        
        view.findViewById<View>(R.id.btnAttachPhoto).setOnClickListener {
            dialog.dismiss()
            pickPhoto.launch("image/*")
        }
        view.findViewById<View>(R.id.btnAttachVideo).setOnClickListener {
            dialog.dismiss()
            pickVideo.launch("video/*")
        }
        view.findViewById<View>(R.id.btnAttachDocument).setOnClickListener {
            dialog.dismiss()
            pickDocument.launch("*/*")
        }
        
        dialog.show()
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

    private fun cancelReplyOrEdit() {
        replyToMessageId = 0L
        editMessageId = 0L
        layoutReplyPreview.visibility = View.GONE
        editMessage.setText("")
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
            val msgRoot: LinearLayout = view.findViewById(R.id.msgRoot)
            val messageBubble: LinearLayout = view.findViewById(R.id.messageBubble)
            val imgMsgAvatar: android.widget.ImageView = view.findViewById(R.id.imgMsgAvatar)
            val txtSender: TextView = view.findViewById(R.id.txtSender)
            val imgMedia: android.widget.ImageView = view.findViewById(R.id.imgMedia)
            val txtContent: TextView = view.findViewById(R.id.txtContent)
            val txtTime: TextView = view.findViewById(R.id.txtTime)
            val imgStatus: android.widget.ImageView = view.findViewById(R.id.imgStatus)
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

            // Setup Bubble and Alignment
            if (isOutgoing) {
                holder.msgRoot.gravity = Gravity.END
                holder.messageBubble.setBackgroundResource(R.drawable.bg_msg_out)
                holder.imgMsgAvatar.visibility = View.GONE
                holder.txtSender.visibility = View.GONE
                
                holder.imgStatus.visibility = View.VISIBLE
                // Check message state
                // TdApi.MessageSendingStateFailed / Pending
            } else {
                holder.msgRoot.gravity = Gravity.START
                holder.messageBubble.setBackgroundResource(R.drawable.bg_msg_in)
                holder.imgStatus.visibility = View.GONE
                
                // Show avatar and sender in group chats? For simplicity, we just hide avatar if it's 1v1
                val chat = TdClient.chats[chatId]
                if (chat?.type?.constructor == TdApi.ChatTypeSupergroup.CONSTRUCTOR || chat?.type?.constructor == TdApi.ChatTypeBasicGroup.CONSTRUCTOR) {
                    holder.imgMsgAvatar.visibility = View.VISIBLE
                    holder.txtSender.visibility = View.VISIBLE
                    
                    val senderId = msg.senderId
                    val senderName = when (senderId?.constructor) {
                        TdApi.MessageSenderUser.CONSTRUCTOR -> {
                            val userId = (senderId as TdApi.MessageSenderUser).userId
                            "User $userId"
                        }
                        TdApi.MessageSenderChat.CONSTRUCTOR -> {
                            val senderChatId = (senderId as TdApi.MessageSenderChat).chatId
                            TdClient.chats[senderChatId]?.title ?: "Chat $senderChatId"
                        }
                        else -> "Unknown"
                    }
                    holder.txtSender.text = senderName
                } else {
                    holder.imgMsgAvatar.visibility = View.GONE
                    holder.txtSender.visibility = View.GONE
                }
            }

            // Content and Media
            holder.imgMedia.visibility = View.GONE
            holder.imgMedia.setImageDrawable(null)
            val progress = holder.itemView.findViewById<ProgressBar>(R.id.progressMedia)
            progress.visibility = View.GONE
            val imgPlay = holder.itemView.findViewById<ImageView>(R.id.imgPlayVideo)
            imgPlay.visibility = View.GONE

            val content = msg.content
            if (content is TdApi.MessageText) {
                holder.txtContent.visibility = View.VISIBLE
                holder.txtContent.text = content.text.text
            } else if (content is TdApi.MessagePhoto) {
                holder.imgMedia.visibility = View.VISIBLE
                holder.txtContent.visibility = if (content.caption.text.isNotEmpty()) View.VISIBLE else View.GONE
                holder.txtContent.text = content.caption.text
                
                val photoSize = content.photo.sizes.maxByOrNull { it.width }
                if (photoSize != null) {
                    val file = photoSize.photo
                    if (file.local.isDownloadingCompleted) {
                        com.bumptech.glide.Glide.with(holder.itemView.context)
                            .load(file.local.path)
                            .into(holder.imgMedia)
                            
                        holder.imgMedia.setOnClickListener {
                            val intent = Intent(this@ChatActivity, PhotoViewerActivity::class.java)
                            intent.putExtra("photo_path", file.local.path)
                            startActivity(intent)
                        }
                    } else {
                        holder.imgMedia.setOnClickListener(null)
                        // Load thumbnail if available
                        val minithumbnail = content.photo.minithumbnail
                        if (minithumbnail != null) {
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(minithumbnail.data, 0, minithumbnail.data.size)
                            holder.imgMedia.setImageBitmap(bmp)
                        }
                        
                        if (file.local.isDownloadingActive) {
                            progress.visibility = View.VISIBLE
                        } else if (file.local.canBeDownloaded) {
                            TdClient.currentAccount.client?.send(TdApi.DownloadFile(file.id, 1, 0, 0, true)) {}
                            progress.visibility = View.VISIBLE
                        }
                    }
                }
            } else if (content is TdApi.MessageVideo) {
                holder.imgMedia.visibility = View.VISIBLE
                imgPlay.visibility = View.VISIBLE
                holder.txtContent.visibility = if (content.caption.text.isNotEmpty()) View.VISIBLE else View.GONE
                holder.txtContent.text = content.caption.text
                
                val file = content.video.video
                val thumbnail = content.video.thumbnail?.file
                
                if (file.local.isDownloadingCompleted) {
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(file.local.path)
                        .into(holder.imgMedia)
                        
                    holder.imgMedia.setOnClickListener {
                        val intent = Intent(this@ChatActivity, VideoPlayerActivity::class.java)
                        intent.putExtra("video_path", file.local.path)
                        startActivity(intent)
                    }
                } else {
                    if (file.local.canBeDownloaded && !file.local.isDownloadingActive) {
                        holder.imgMedia.setOnClickListener {
                            TdClient.currentAccount.client?.send(TdApi.DownloadFile(file.id, 32, 0, 0, true)) {}
                        }
                    } else if (file.local.isDownloadingActive) {
                        progress.visibility = View.VISIBLE
                    }
                    
                    if (thumbnail != null) {
                        if (thumbnail.local.isDownloadingCompleted) {
                            com.bumptech.glide.Glide.with(holder.itemView.context)
                                .load(thumbnail.local.path)
                                .into(holder.imgMedia)
                        } else if (thumbnail.local.canBeDownloaded && !thumbnail.local.isDownloadingActive) {
                            TdClient.currentAccount.client?.send(TdApi.DownloadFile(thumbnail.id, 1, 0, 0, true)) {}
                        }
                    }
                }
            } else {
                holder.txtContent.visibility = View.VISIBLE
                holder.txtContent.text = TdClient.getMessageText(content)
            }

            // Time
            holder.txtTime.text = formatTime(msg.date)
            
            // Long Press Options
            holder.messageBubble.setOnLongClickListener {
                showOptionsDialog(msg)
                true
            }
        }
    }

    private fun showOptionsDialog(msg: TdApi.Message) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_message_options, null)
        dialog.setContentView(view)
        
        val btnReply = view.findViewById<TextView>(R.id.btnReply)
        val btnCopy = view.findViewById<TextView>(R.id.btnCopy)
        val btnEdit = view.findViewById<TextView>(R.id.btnEdit)
        val btnForward = view.findViewById<TextView>(R.id.btnForward)
        val btnDelete = view.findViewById<TextView>(R.id.btnDelete)
        
        // Only show edit for outgoing text messages
        if (msg.isOutgoing && msg.content is TdApi.MessageText) {
            btnEdit.visibility = View.VISIBLE
        }
        
        btnReply.setOnClickListener {
            dialog.dismiss()
            replyToMessageId = msg.id
            editMessageId = 0L
            layoutReplyPreview.visibility = View.VISIBLE
            txtReplyName.text = "Reply to message"
            txtReplyText.text = TdClient.getMessageText(msg.content)
            editMessage.requestFocus()
        }
        
        btnCopy.setOnClickListener {
            dialog.dismiss()
            val content = msg.content
            if (content is TdApi.MessageText) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Message", content.text.text))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnEdit.setOnClickListener {
            dialog.dismiss()
            if (msg.content is TdApi.MessageText) {
                editMessageId = msg.id
                replyToMessageId = 0L
                layoutReplyPreview.visibility = View.VISIBLE
                txtReplyName.text = "Edit message"
                txtReplyText.text = TdClient.getMessageText(msg.content)
                editMessage.setText((msg.content as TdApi.MessageText).text.text)
                editMessage.requestFocus()
            }
        }
        
        btnForward.setOnClickListener {
            dialog.dismiss()
            // simple forward to same chat for now
            TdClient.forwardMessage(chatId, chatId, msg.id)
            Toast.makeText(this, "Forwarded to this chat", Toast.LENGTH_SHORT).show()
        }
        
        btnDelete.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Delete") { _, _ ->
                    TdClient.deleteMessage(chatId, msg.id, true)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        dialog.show()
    }

    private fun formatTime(timestamp: Int): String {
        val date = Date(timestamp.toLong() * 1000)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(date)
    }
}
