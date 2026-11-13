
package com.kingai.assistant

import android.content.Context
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File

class TelegramAccountManager private constructor(context: Context) {

    companion object {
        const val ACCOUNT_ONE = "account1"
        const val ACCOUNT_TWO = "account2"

        @Volatile
        private var instance: TelegramAccountManager? = null

        fun get(context: Context): TelegramAccountManager {
            return instance ?: synchronized(this) {
                instance ?: TelegramAccountManager(
                    context.applicationContext
                ).also { instance = it }
            }
        }
    }

    data class AccountInfo(
        val id: String,
        val name: String = "حساب تلگرام",
        val phone: String = "",
        val status: String = "اتصال برقرار نشده",
        val authorized: Boolean = false
    )

    data class IncomingMessage(
        val accountId: String,
        val chatId: Long,
        val messageId: Long,
        val sender: String,
        val message: String,
        val date: Int
    )

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val clients = mutableMapOf<String, TdClient>()

    private val preferences = appContext.getSharedPreferences(
        "telegram_account_sessions",
        Context.MODE_PRIVATE
    )

    private val autoReplyPreferences =
        appContext.getSharedPreferences(
            "telegram_auto_reply",
            Context.MODE_PRIVATE
        )

    private val _accounts = MutableStateFlow(
        mapOf(
            ACCOUNT_ONE to AccountInfo(ACCOUNT_ONE),
            ACCOUNT_TWO to AccountInfo(ACCOUNT_TWO)
        )
    )

    val accounts: StateFlow<Map<String, AccountInfo>> =
        _accounts.asStateFlow()

    private val _messages =
        MutableStateFlow<Map<String, List<IncomingMessage>>>(
            mapOf(
                ACCOUNT_ONE to emptyList(),
                ACCOUNT_TWO to emptyList()
            )
        )

    val messages: StateFlow<Map<String, List<IncomingMessage>>> =
        _messages.asStateFlow()

    private val replyLock = Any()
    private val processingChats = mutableSetOf<String>()

    init {
        restoreSavedAccounts()
    }

    private fun updateAccount(
        id: String,
        transform: (AccountInfo) -> AccountInfo
    ) {
        val current = _accounts.value[id] ?: return

        _accounts.value = _accounts.value.toMutableMap().apply {
            put(id, transform(current))
        }
    }

    private fun addIncomingMessage(
        message: IncomingMessage
    ) {
        val current = _messages.value[message.accountId]
            ?: emptyList()

        if (current.any {
                it.chatId == message.chatId &&
                it.messageId == message.messageId
            }) {
            return
        }

        val updated = (current + message).takeLast(200)

        _messages.value = _messages.value.toMutableMap().apply {
            put(message.accountId, updated)
        }
    }

    fun clearMessages(id: String) {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) return

        _messages.value = _messages.value.toMutableMap().apply {
            put(id, emptyList())
        }
    }

    // روشن یا خاموش کردن پاسخ خودکار برای هر حساب
    fun setAutoReplyEnabled(
        id: String,
        enabled: Boolean
    ) {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) return

        autoReplyPreferences.edit()
            .putBoolean("enabled_$id", enabled)
            .apply()
    }

    fun isAutoReplyEnabled(id: String): Boolean {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) return false

        return autoReplyPreferences.getBoolean(
            "enabled_$id",
            true
        )
    }

    private fun handleNewMessage(
        accountId: String,
        client: TdClient,
        update: TdApi.UpdateNewMessage
    ) {
        val message = update.message ?: return

        if (message.isOutgoing) return

        val content = message.content
        if (content !is TdApi.MessageText) return

        val text = content.text?.text?.trim().orEmpty()
        if (text.isBlank()) return

        scope.launch {
            try {
                val result = client.send(
                    TdApi.GetChat(message.chatId)
                )

                if (result !is TdApi.Chat) return@launch

                // فقط چت خصوصی
                if (result.type !is TdApi.ChatTypePrivate) {
                    return@launch
                }

                val sender = result.title
                    .takeIf { it.isNotBlank() }
                    ?: "مخاطب تلگرام"

                addIncomingMessage(
                    IncomingMessage(
                        accountId = accountId,
                        chatId = message.chatId,
                        messageId = message.id,
                        sender = sender,
                        message = text,
                        date = message.date
                    )
                )

                // پاسخ خودکار
                sendAutoReply(
                    client = client,
                    accountId = accountId,
                    chatId = message.chatId,
                    messageId = message.id,
                    text = text
                )

            } catch (_: Exception) {
                // دریافت پیام‌های بعدی ادامه پیدا می‌کند
            }
        }
    }

    private suspend fun sendAutoReply(
        client: TdClient,
        accountId: String,
        chatId: Long,
        messageId: Long,
        text: String
    ) {
        if (!isAutoReplyEnabled(accountId)) return

        val chatKey = "${accountId}_$chatId"

        synchronized(replyLock) {
            if (!processingChats.add(chatKey)) {
                return
            }
        }

        try {
            val lastMessageKey = "last_message_$chatKey"

            val previousMessageId =
                autoReplyPreferences.getLong(
                    lastMessageKey,
                    0L
                )

            // جلوگیری از پاسخ دوباره به همان پیام
            if (messageId <= previousMessageId) return

            autoReplyPreferences.edit()
                .putLong(lastMessageKey, messageId)
                .apply()

            val normalized = text.trim().lowercase()

            val greetings = listOf(
                "سلام",
                "درود",
                "hello",
                "hi",
                "hey"
            )

            val isGreeting = greetings.any { greeting ->
                normalized == greeting ||
                normalized.startsWith("$greeting ") ||
                normalized.startsWith("$greeting،") ||
                normalized.startsWith("$greeting,")
            }

            val now = System.currentTimeMillis()

            val greetingKey = "greeting_$chatKey"
            val offlineKey = "offline_$chatKey"

            val lastGreeting =
                autoReplyPreferences.getLong(greetingKey, 0L)

            val lastOffline =
                autoReplyPreferences.getLong(offlineKey, 0L)

            val twelveHours = 12 * 60 * 60 * 1000L
            val sixHours = 6 * 60 * 60 * 1000L

            val reply: String
            val cooldownKey: String

            if (isGreeting) {

                if (now - lastGreeting < twelveHours) {
                    return
                }

                reply = "سلام، خوبی؟ چه خبر؟"
                cooldownKey = greetingKey

            } else {

                if (now - lastOffline < sixHours) {
                    return
                }

                reply =
                    "داداش، فعلاً آنلاین نیستم. آنلاین شدم جوابتو می‌دم."

                cooldownKey = offlineKey
            }

            val inputContent = TdApi.InputMessageText(
                TdApi.FormattedText(reply, null),
                null,
                true
            )

            val result = client.send(
                TdApi.SendMessage(
                    chatId,
                    0L,
                    null,
                    null,
                    null,
                    inputContent
                )
            )

            if (result is TdApi.Message) {
                autoReplyPreferences.edit()
                    .putLong(cooldownKey, now)
                    .apply()
            }

        } catch (_: Exception) {
            // خطای ارسال نباید برنامه را متوقف کند
        } finally {
            synchronized(replyLock) {
                processingChats.remove(chatKey)
            }
        }
    }

    fun rememberAccount(id: String) {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) return

        val saved = preferences.getStringSet(
            "saved_account_ids",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        saved.add(id)

        preferences.edit()
            .putStringSet("saved_account_ids", saved)
            .apply()
    }

    private fun forgetAccount(id: String) {
        val saved = preferences.getStringSet(
            "saved_account_ids",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        saved.remove(id)

        preferences.edit()
            .putStringSet("saved_account_ids", saved)
            .apply()
    }

    private fun restoreSavedAccounts() {
        val saved = preferences.getStringSet(
            "saved_account_ids",
            emptySet()
        ) ?: emptySet()

        saved.forEach { id ->
            if (id == ACCOUNT_ONE || id == ACCOUNT_TWO) {
                startAccount(id)
            }
        }
    }

    fun startAccount(id: String): Boolean {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) {
            return false
        }

        if (clients.containsKey(id)) return true

        val apiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull()
            ?: run {
                updateAccount(id) {
                    it.copy(status = "API ID تنظیم نشده")
                }
                return false
            }

        val apiHash = BuildConfig.TELEGRAM_API_HASH

        if (apiHash.isBlank()) {
            updateAccount(id) {
                it.copy(status = "API HASH تنظیم نشده")
            }
            return false
        }

        return try {
            System.loadLibrary("tdjni")

            val directory = File(
                appContext.filesDir,
                "telegram_accounts/$id"
            )

            directory.mkdirs()

            val client = TdClient(
                filesDir = directory.absolutePath,
                verbosityLevel = 1,
                apiId = apiId,
                apiHash = apiHash
            )

            clients[id] = client

            updateAccount(id) {
                it.copy(status = "در حال اتصال به تلگرام...")
            }

            scope.launch {
                try {
                    client.updates.collect { update ->
                        when (update) {
                            is TdApi.UpdateAuthorizationState -> {
                                handleAuthorization(
                                    id,
                                    client,
                                    update
                                )
                            }

                            is TdApi.UpdateNewMessage -> {
                                handleNewMessage(
                                    id,
                                    client,
                                    update
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    updateAccount(id) {
                        it.copy(
                            status = "خطا در دریافت اطلاعات: ${
                                e.message ?: "نامشخص"
                            }"
                        )
                    }
                }
            }

            client.init()
            rememberAccount(id)

            true

        } catch (e: Exception) {
            clients.remove(id)

            updateAccount(id) {
                it.copy(
                    status = "خطا: ${
                        e.message ?: "اتصال ناموفق"
                    }"
                )
            }

            false
        }
    }

    private fun handleAuthorization(
        id: String,
        client: TdClient,
        update: TdApi.UpdateAuthorizationState
    ) {
        when (update.authorizationState) {

            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                updateAccount(id) {
                    it.copy(status = "در حال آماده‌سازی...")
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                updateAccount(id) {
                    it.copy(
                        status = "منتظر شماره تلفن",
                        authorized = false
                    )
                }
            }

            is TdApi.AuthorizationStateWaitCode -> {
                updateAccount(id) {
                    it.copy(status = "کد تأیید ارسال شد")
                }
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                updateAccount(id) {
                    it.copy(status = "منتظر رمز دومرحله‌ای")
                }
            }

            is TdApi.AuthorizationStateWaitEmailAddress -> {
                updateAccount(id) {
                    it.copy(status = "منتظر ایمیل تأیید")
                }
            }

            is TdApi.AuthorizationStateWaitEmailCode -> {
                updateAccount(id) {
                    it.copy(status = "منتظر کد ایمیل")
                }
            }

            is TdApi.AuthorizationStateWaitRegistration -> {
                updateAccount(id) {
                    it.copy(status = "نیازمند ثبت‌نام")
                }
            }

            is TdApi.AuthorizationStateReady -> {
                updateAccount(id) {
                    it.copy(
                        status = "متصل",
                        authorized = true
                    )
                }

                scope.launch {
                    try {
                        val me = client.send(TdApi.GetMe())

                        if (me is TdApi.User) {
                            val fullName = listOf(
                                me.firstName,
                                me.lastName
                            )
                                .filter { it.isNotBlank() }
                                .joinToString(" ")

                            updateAccount(id) {
                                it.copy(
                                    name = fullName.ifBlank {
                                        "حساب تلگرام"
                                    },
                                    phone = me.phoneNumber,
                                    authorized = true,
                                    status = "متصل"
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            is TdApi.AuthorizationStateClosed -> {
                updateAccount(id) {
                    it.copy(
                        status = "نشست بسته شد",
                        authorized = false
                    )
                }
            }

            else -> {
                updateAccount(id) {
                    it.copy(status = "در حال بررسی وضعیت...")
                }
            }
        }
    }

    fun submitPhone(id: String, phone: String) {
        val client = clients[id] ?: run {
            updateAccount(id) {
                it.copy(status = "خطا: اتصال حساب برقرار نیست")
            }
            return
        }

        val cleanPhone = phone.trim().replace(" ", "")

        if (
            !cleanPhone.startsWith("+") ||
            cleanPhone.length < 8 ||
            cleanPhone.drop(1).any { !it.isDigit() }
        ) {
            updateAccount(id) {
                it.copy(
                    status = "شماره نامعتبر است؛ با کد کشور وارد کن"
                )
            }
            return
        }

        updateAccount(id) {
            it.copy(status = "در حال ارسال شماره...")
        }

        scope.launch {
            try {
                val result = client.send(
                    TdApi.SetAuthenticationPhoneNumber(
                        cleanPhone,
                        null
                    )
                )

                if (result is TdApi.Error) {
                    updateAccount(id) {
                        it.copy(
                            status = "خطای تلگرام ${result.code}: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(
                        status = "خطای ارسال شماره: ${
                            e.message ?: e.javaClass.simpleName
                        }"
                    )
                }
            }
        }
    }

    fun submitCode(id: String, code: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                val result = client.send(
                    TdApi.CheckAuthenticationCode(code.trim())
                )

                if (result is TdApi.Error) {
                    updateAccount(id) {
                        it.copy(
                            status = "خطای کد ${result.code}: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(
                        status = "خطای کد: ${e.message ?: "نامشخص"}"
                    )
                }
            }
        }
    }

    fun submitPassword(id: String, password: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                val result = client.send(
                    TdApi.CheckAuthenticationPassword(password)
                )

                if (result is TdApi.Error) {
                    updateAccount(id) {
                        it.copy(
                            status = "خطای رمز ${result.code}: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(
                        status = "خطای رمز: ${e.message ?: "نامشخص"}"
                    )
                }
            }
        }
    }

    fun submitEmail(id: String, email: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                val result = client.send(
                    TdApi.SetAuthenticationEmailAddress(email.trim())
                )

                if (result is TdApi.Error) {
                    updateAccount(id) {
                        it.copy(
                            status = "خطای ایمیل ${result.code}: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(
                        status = "خطای ایمیل: ${e.message ?: "نامشخص"}"
                    )
                }
            }
      
