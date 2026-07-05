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

class AccountState(val accountId: Int, val databaseDir: String) {
    var client: Client? = null
    var authState: TdApi.AuthorizationState? = null
    var myUserId: Long = 0L
    val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    val users = ConcurrentHashMap<Long, TdApi.User>()
    val orderedChatIds: MutableList<Long> = Collections.synchronizedList(mutableListOf())
}

/**
 * Singleton wrapper for TDLib client.
 * Manages authentication, chat list, and message operations for multiple accounts.
 */
object TdClient {

    private const val TAG = "TdClient"

    // ---- REPLACE THESE WITH YOUR OWN CREDENTIALS ----
    private const val API_ID = 35654732
    private const val API_HASH = "155109668b05aeb8b99afce4f8496524"
    // -------------------------------------------------

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var baseDatabaseDir: String

    val accounts = ConcurrentHashMap<Int, AccountState>()

    val currentAccount: AccountState
        get() = getOrCreateAccount(AccountManager.activeAccountId)

    // Exposed properties pointing to current account
    val authState: TdApi.AuthorizationState? get() = currentAccount.authState
    val myUserId: Long get() = currentAccount.myUserId
    val chats: ConcurrentHashMap<Long, TdApi.Chat> get() = currentAccount.chats
    val users: ConcurrentHashMap<Long, TdApi.User> get() = currentAccount.users
    val orderedChatIds: MutableList<Long> get() = currentAccount.orderedChatIds

    // ---- Callbacks (set by Activities, triggered for active account only) ----
    var onAuthStateChanged: ((TdApi.AuthorizationState) -> Unit)? = null
    var onChatsUpdated: (() -> Unit)? = null
    var onNewMessage: ((TdApi.Message) -> Unit)? = null
    var onFileUpdated: ((TdApi.File) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init(context: Context) {
        baseDatabaseDir = context.filesDir.absolutePath + "/tdlib_acc"

        // Reduce log noise
        Client.execute(TdApi.SetLogVerbosityLevel(2))

        // Initialize all saved accounts
        for (id in AccountManager.getAccountIds()) {
            getOrCreateAccount(id)
        }
    }

    fun getOrCreateAccount(id: Int): AccountState {
        return accounts.getOrPut(id) {
            val state = AccountState(id, "${baseDatabaseDir}_$id")
            state.client = Client.create(
                { update -> handleUpdate(id, update) },
                { e -> Log.e(TAG, "TDLib update exception", e) },
                { e -> Log.e(TAG, "TDLib default exception", e) }
            )
            state
        }
    }

    fun switchAccount(id: Int) {
        AccountManager.switchAccount(id)
        mainHandler.post {
            onAuthStateChanged?.invoke(currentAccount.authState ?: TdApi.AuthorizationStateWaitTdlibParameters())
            onChatsUpdated?.invoke()
        }
    }

    // ---- Authentication ----

    fun sendPhoneNumber(phone: String) {
        currentAccount.client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            handleError(result)
        }
    }

    fun sendCode(code: String) {
        currentAccount.client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            handleError(result)
        }
    }

    fun sendPassword(password: String) {
        currentAccount.client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            handleError(result)
        }
    }

    fun logout() {
        currentAccount.client?.send(TdApi.LogOut()) { result ->
            handleError(result)
        }
    }

    // ---- Chats ----

    fun loadChats() {
        currentAccount.client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 50)) { result ->
            if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                val error = result as TdApi.Error
                if (error.code != 404) {
                    postError(error.message)
                }
            }
        }
    }

    fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int, callback: (List<TdApi.Message>) -> Unit) {
        currentAccount.client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) { result ->
            if (result.constructor == TdApi.Messages.CONSTRUCTOR) {
                val messages = (result as TdApi.Messages).messages?.toList() ?: emptyList()
                mainHandler.post { callback(messages) }
            } else {
                handleError(result)
                mainHandler.post { callback(emptyList()) }
            }
        }
    }

    // ---- Send & Modify Messages ----

    fun sendTextMessage(chatId: Long, text: String, replyToMessageId: Long = 0L) {
        val formattedText = TdApi.FormattedText(text, emptyArray())
        val content = TdApi.InputMessageText().apply {
            this.text = formattedText
        }
        doSendMessage(chatId, content, replyToMessageId)
    }

    fun sendPhoto(chatId: Long, filePath: String, replyToMessageId: Long = 0L) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessagePhoto().apply {
            photo = TdApi.InputPhoto().apply {
                photo = TdApi.InputFileLocal(filePath)
            }
            this.caption = caption
        }
        doSendMessage(chatId, content, replyToMessageId)
    }

    fun sendVideo(chatId: Long, filePath: String, replyToMessageId: Long = 0L) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessageVideo().apply {
            video = TdApi.InputVideo().apply {
                video = TdApi.InputFileLocal(filePath)
            }
            this.caption = caption
        }
        doSendMessage(chatId, content, replyToMessageId)
    }

    fun sendDocument(chatId: Long, filePath: String, replyToMessageId: Long = 0L) {
        val caption = TdApi.FormattedText("", emptyArray())
        val content = TdApi.InputMessageDocument().apply {
            document = TdApi.InputDocument().apply {
                document = TdApi.InputFileLocal(filePath)
            }
            this.caption = caption
        }
        doSendMessage(chatId, content, replyToMessageId)
    }

    private fun doSendMessage(chatId: Long, content: TdApi.InputMessageContent, replyToMessageId: Long = 0L) {
        val msg = TdApi.SendMessage().apply {
            this.chatId = chatId
            if (replyToMessageId != 0L) {
                this.replyTo = TdApi.InputMessageReplyToMessage().apply { 
                    this.messageId = replyToMessageId 
                }
            }
            this.inputMessageContent = content
        }
        currentAccount.client?.send(msg) { result ->
            handleError(result)
        }
    }

    fun deleteMessage(chatId: Long, messageId: Long, revoke: Boolean) {
        currentAccount.client?.send(TdApi.DeleteMessages(chatId, longArrayOf(messageId), revoke)) { result ->
            handleError(result)
        }
    }

    fun editMessageText(chatId: Long, messageId: Long, newText: String) {
        val formattedText = TdApi.FormattedText(newText, emptyArray())
        val content = TdApi.InputMessageText().apply {
            this.text = formattedText
        }
        currentAccount.client?.send(TdApi.EditMessageText(chatId, messageId, null, content)) { result ->
            handleError(result)
        }
    }

    fun forwardMessage(chatId: Long, fromChatId: Long, messageId: Long) {
        val forwardMessages = TdApi.ForwardMessages().apply {
            this.chatId = chatId
            this.fromChatId = fromChatId
            this.messageIds = longArrayOf(messageId)
            this.sendCopy = false
            this.removeCaption = false
        }
        currentAccount.client?.send(forwardMessages) { result ->
            handleError(result)
        }
    }

    // Mark chat as read
    fun openChat(chatId: Long) {
        currentAccount.client?.send(TdApi.OpenChat(chatId)) {}
    }

    fun closeChat(chatId: Long) {
        currentAccount.client?.send(TdApi.CloseChat(chatId)) {}
    }

    // ---- Update Handler ----

    private fun handleUpdate(accountId: Int, update: TdApi.Object) {
        val state = accounts[accountId] ?: return
        val isActiveAccount = (accountId == AccountManager.activeAccountId)

        when (update.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val auth = (update as TdApi.UpdateAuthorizationState).authorizationState
                state.authState = auth

                when (auth.constructor) {
                    TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                        val params = TdApi.SetTdlibParameters().apply {
                            apiId = API_ID
                            apiHash = API_HASH
                            databaseDirectory = state.databaseDir
                            useMessageDatabase = true
                            useSecretChats = false
                            systemLanguageCode = "en"
                            deviceModel = Build.MODEL
                            systemVersion = Build.VERSION.RELEASE
                            applicationVersion = "1.0"
                        }
                        state.client?.send(params) { result -> handleError(result) }
                    }
                    TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                        state.client?.send(TdApi.GetMe()) { result ->
                            if (result.constructor == TdApi.User.CONSTRUCTOR) {
                                state.myUserId = (result as TdApi.User).id
                            }
                        }
                    }
                    TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                        accounts.remove(accountId)
                        AccountManager.removeAccountId(accountId)
                        if (isActiveAccount) {
                            val nextIds = AccountManager.getAccountIds()
                            if (nextIds.isNotEmpty()) {
                                switchAccount(nextIds.first())
                            } else {
                                val newId = AccountManager.createNewAccount()
                                switchAccount(newId)
                            }
                        }
                    }
                }
                
                if (isActiveAccount) {
                    mainHandler.post { onAuthStateChanged?.invoke(auth) }
                }
            }

            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val chat = (update as TdApi.UpdateNewChat).chat
                state.chats[chat.id] = chat
                addChatToOrderedList(state, chat)
                if (isActiveAccount) mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateUser.CONSTRUCTOR -> {
                val user = (update as TdApi.UpdateUser).user
                state.users[user.id] = user
            }

            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatPosition
                val chat = state.chats[upd.chatId] ?: return
                val positions = chat.positions?.toMutableList() ?: mutableListOf()
                val idx = positions.indexOfFirst {
                    it.list?.constructor == upd.position.list?.constructor
                }
                if (upd.position.order == 0L) {
                    if (idx >= 0) positions.removeAt(idx)
                    state.orderedChatIds.remove(upd.chatId)
                } else {
                    if (idx >= 0) positions[idx] = upd.position
                    else positions.add(upd.position)
                    if (!state.orderedChatIds.contains(upd.chatId)) {
                        state.orderedChatIds.add(upd.chatId)
                    }
                }
                chat.positions = positions.toTypedArray()
                sortChatIds(state)
                if (isActiveAccount) mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatLastMessage
                val chat = state.chats[upd.chatId] ?: return
                chat.lastMessage = upd.lastMessage
                if (upd.positions != null && upd.positions.isNotEmpty()) {
                    chat.positions = upd.positions
                    sortChatIds(state)
                }
                if (isActiveAccount) mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val upd = update as TdApi.UpdateChatTitle
                val chat = state.chats[upd.chatId] ?: return
                chat.title = upd.title
                if (isActiveAccount) mainHandler.post { onChatsUpdated?.invoke() }
            }

            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val msg = (update as TdApi.UpdateNewMessage).message
                if (isActiveAccount) mainHandler.post { onNewMessage?.invoke(msg) }
            }
            
            TdApi.UpdateFile.CONSTRUCTOR -> {
                val file = (update as TdApi.UpdateFile).file
                if (isActiveAccount) mainHandler.post { onFileUpdated?.invoke(file) }
            }
        }
    }

    // ---- Helpers ----

    private fun addChatToOrderedList(state: AccountState, chat: TdApi.Chat) {
        val hasMainPosition = chat.positions?.any {
            it.list?.constructor == TdApi.ChatListMain.CONSTRUCTOR && it.order != 0L
        } ?: false

        if (hasMainPosition && !state.orderedChatIds.contains(chat.id)) {
            state.orderedChatIds.add(chat.id)
            sortChatIds(state)
        }
    }

    private fun sortChatIds(state: AccountState) {
        synchronized(state.orderedChatIds) {
            state.orderedChatIds.sortByDescending { chatId ->
                val chat = state.chats[chatId]
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
