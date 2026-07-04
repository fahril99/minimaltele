package com.minimaltelegram

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

/**
 * Singleton wrapper for TDLib client.
 * Manages authentication, chat list, and message operations.
 */
object TdClient {

    private const val TAG = "TdClient"

    // ---- REPLACE THESE WITH YOUR OWN CREDENTIALS ----
    // Get them from https://my.telegram.org/
    private const val API_ID = 35654732        // <-- Your api_id here
    private const val API_HASH = "155109668b05aeb8b99afce4f8496524"     // <-- Your api_hash here
    // -------------------------------------------------

    private var client: Client? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var databaseDir: String

    // Current authorization state
    var authState: TdApi.AuthorizationState? = null
        private set

    // My user ID (set after auth ready)
    var myUserId: Long = 0L
        private set

    // Chat cache
    val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    val orderedChatIds: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    // ---- Callbacks (set by Activities) ----
    var onAuthStateChanged: ((TdApi.AuthorizationState) -> Unit)? = null
    var onChatsUpdated: (() -> Unit)? = null
    var onNewMessage: ((TdApi.Message) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init(context: Context) {
        databaseDir = context.filesDir.absolutePath + "/tdlib"

        // Reduce log noise
        Client.execute(TdApi.SetLogVerbosityLevel(2))

        client = Client.create(
            { update -> handleUpdate(update) },
            { e -> Log.e(TAG, "TDLib update exception", e) },
            { e -> Log.e(TAG, "TDLib default exception", e) }
        )
    }

    // ---- Authentication ----

    fun sendPhoneNumber(phone: String) {
        // Pass null settings to use TDLib defaults (safest across versions)
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            handleError(result)
        }
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            handleError(result)
        }
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            handleError(result)
        }
    }

    fun logout() {
        client?.send(TdApi.LogOut()) { result ->
            handleError(result)
        }
    }

    // ---- Chats ----

    fun loadChats() {
        client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 50)) { result ->
            if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                val error = result as TdApi.Error
                // 404 means all chats loaded — not a real error
                if (error.code != 404) {
                    postError(error.message)
                }
            }
        }
    }

    fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int, callback: (List<TdApi.Message>) -> Unit) {
        client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) { result ->
            if (result.constructor == TdApi.Messages.CONSTRUCTOR) {
                val messages = (result as TdApi.Messages).messages?.toList() ?: emptyList()
                mainHandler.post { callback(messages) }
            } else {
                handleError(result)
                mainHandler.post { callback(emptyList()) }
            }
        }
    }

    // ---- Send Messages ----

    fun sendTextMessage(chatId: Long, text: String) {
        val formattedText = TdApi.FormattedText(text, emptyArray())
        val content = TdApi.InputMessageText().apply {
            this.text = formattedText
        }
        doSendMessage(chatId, content)
    }

    fun sendPhoto(chatId: Long, filePath: String) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessagePhoto().apply {
            photo = TdApi.InputFileLocal(filePath)
            this.caption = caption
        }
        doSendMessage(chatId, content)
    }

    fun sendVideo(chatId: Long, filePath: String) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessageVideo().apply {
            video = TdApi.InputFileLocal(filePath)
            this.caption = caption
        }
        doSendMessage(chatId, content)
    }

    fun sendDocument(chatId: Long, filePath: String) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessageDocument().apply {
            document = TdApi.InputFileLocal(filePath)
            this.caption = caption
        }
        doSendMessage(chatId, content)
    }

    private fun doSendMessage(chatId: Long, content: TdApi.InputMessageContent) {
        val msg = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }
        client?.send(msg) { result ->
            handleError(result)
        }
    }

    // Mark chat as read
    fun openChat(chatId: Long) {
        client?.send(TdApi.OpenChat(chatId)) {}
    }

    fun closeChat(chatId: Long) {
        client?.send(TdApi.CloseChat(chatId)) {}
    }

    // ---- Update Handler ----

    private fun handleUpdate(update: TdApi.Object) {
        when (update.constructor) {

            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val state = (update as TdApi.UpdateAuthorizationState).authorizationState
                authState = state
                onAuthorizationStateUpdated(state)
                mainHandler.post { onAuthStateChanged?.invoke(state) }
            }

            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val chat = (update as TdApi.UpdateNewChat).chat
                chats[chat.id] = chat
                addChatToOrderedList(chat)
                mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatPosition
                val chat = chats[upd.chatId] ?: return
                // Update position in the chat object
                val positions = chat.positions?.toMutableList() ?: mutableListOf()
                val idx = positions.indexOfFirst {
                    it.list?.constructor == upd.position.list?.constructor
                }
                if (upd.position.order == 0L) {
                    // Removed from list
                    if (idx >= 0) positions.removeAt(idx)
                    orderedChatIds.remove(upd.chatId)
                } else {
                    if (idx >= 0) positions[idx] = upd.position
                    else positions.add(upd.position)
                    if (!orderedChatIds.contains(upd.chatId)) {
                        orderedChatIds.add(upd.chatId)
                    }
                }
                chat.positions = positions.toTypedArray()
                sortChatIds()
                mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatLastMessage
                val chat = chats[upd.chatId] ?: return
                chat.lastMessage = upd.lastMessage
                if (upd.positions != null && upd.positions.isNotEmpty()) {
                    chat.positions = upd.positions
                    sortChatIds()
                }
                mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatTitle
                val chat = chats[upd.chatId] ?: return
                chat.title = upd.title
                mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val msg = (update as TdApi.UpdateNewMessage).message
                mainHandler.post { onNewMessage?.invoke(msg) }
            }

            TdApi.UpdateUser.CONSTRUCTOR -> {
                // No-op: user info is fetched via GetMe after AuthorizationStateReady
            }
        }
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val params = TdApi.SetTdlibParameters().apply {
                    apiId = API_ID
                    apiHash = API_HASH
                    databaseDirectory = databaseDir
                    useMessageDatabase = true
                    useSecretChats = false
                    systemLanguageCode = "en"
                    deviceModel = Build.MODEL
                    systemVersion = Build.VERSION.RELEASE
                    applicationVersion = "1.0"
                }
                client?.send(params) { result ->
                    handleError(result)
                }
            }

            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                // Get own user ID
                client?.send(TdApi.GetMe()) { result ->
                    if (result.constructor == TdApi.User.CONSTRUCTOR) {
                        myUserId = (result as TdApi.User).id
                    }
                }
            }

            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                Log.d(TAG, "Logging out...")
            }

            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                // Re-create client for new login
                chats.clear()
                orderedChatIds.clear()
                myUserId = 0L
                client = Client.create(
                    { update -> handleUpdate(update) },
                    { e -> Log.e(TAG, "TDLib update exception", e) },
                    { e -> Log.e(TAG, "TDLib default exception", e) }
                )
            }
        }
    }

    // ---- Helpers ----

    private fun addChatToOrderedList(chat: TdApi.Chat) {
        val hasMainPosition = chat.positions?.any {
            it.list?.constructor == TdApi.ChatListMain.CONSTRUCTOR && it.order != 0L
        } ?: false

        if (hasMainPosition && !orderedChatIds.contains(chat.id)) {
            orderedChatIds.add(chat.id)
            sortChatIds()
        }
    }

    private fun sortChatIds() {
        synchronized(orderedChatIds) {
            orderedChatIds.sortByDescending { chatId ->
                val chat = chats[chatId]
                chat?.positions?.firstOrNull {
                    it.list?.constructor == TdApi.ChatListMain.CONSTRUCTOR
                }?.order ?: 0L
            }
        }
    }

    private fun handleError(result: TdApi.Object) {
        if (result.constructor == TdApi.Error.CONSTRUCTOR) {
            val error = result as TdApi.Error
            Log.e(TAG, "TDLib error: [${error.code}] ${error.message}")
            postError(error.message)
        }
    }

    private fun postError(message: String) {
        mainHandler.post { onError?.invoke(message) }
    }

    /** Helper to get text representation of message content */
    fun getMessageText(content: TdApi.MessageContent?): String {
        return when (content?.constructor) {
            TdApi.MessageText.CONSTRUCTOR -> (content as TdApi.MessageText).text?.text ?: ""
            TdApi.MessagePhoto.CONSTRUCTOR -> "\uD83D\uDDBC Photo" + captionText(content as TdApi.MessagePhoto)
            TdApi.MessageVideo.CONSTRUCTOR -> "\uD83C\uDFA5 Video" + captionText(content as TdApi.MessageVideo)
            TdApi.MessageDocument.CONSTRUCTOR -> "\uD83D\uDCC4 " + ((content as TdApi.MessageDocument).document?.fileName ?: "Document")
            TdApi.MessageAnimation.CONSTRUCTOR -> "GIF"
            TdApi.MessageAudio.CONSTRUCTOR -> "\uD83C\uDFB5 Audio"
            TdApi.MessageVoiceNote.CONSTRUCTOR -> "\uD83C\uDF99 Voice"
            TdApi.MessageVideoNote.CONSTRUCTOR -> "\uD83D\uDCF9 Video message"
            TdApi.MessageSticker.CONSTRUCTOR -> {
                val sticker = (content as TdApi.MessageSticker).sticker
                "${sticker?.emoji ?: ""} Sticker"
            }
            TdApi.MessageContact.CONSTRUCTOR -> "\uD83D\uDC64 Contact"
            TdApi.MessageLocation.CONSTRUCTOR -> "\uD83D\uDCCD Location"
            TdApi.MessagePoll.CONSTRUCTOR -> "\uD83D\uDCCA Poll"
            else -> "[Unsupported message]"
        }
    }

    private fun captionText(msg: TdApi.MessagePhoto): String {
        val caption = msg.caption?.text
        return if (!caption.isNullOrEmpty()) " — $caption" else ""
    }

    private fun captionText(msg: TdApi.MessageVideo): String {
        val caption = msg.caption?.text
        return if (!caption.isNullOrEmpty()) " — $caption" else ""
    }
}
