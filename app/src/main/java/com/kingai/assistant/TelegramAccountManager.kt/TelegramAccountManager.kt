
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

/**
 * مدیریت دو حساب مستقل تلگرام برای KING AI
 * هر حساب پوشه و نشست جداگانه دارد.
 */
class TelegramAccountManager(context: Context) {

    companion object {
        const val ACCOUNT_ONE = "account1"
        const val ACCOUNT_TWO = "account2"
    }

    data class AccountInfo(
        val id: String,
        val name: String = "حساب تلگرام",
        val phone: String = "",
        val status: String = "اتصال برقرار نشده",
        val authorized: Boolean = false
    )

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val clients = mutableMapOf<String, TdClient>()

    private val _accounts =
        MutableStateFlow(
            mapOf(
                ACCOUNT_ONE to AccountInfo(ACCOUNT_ONE),
                ACCOUNT_TWO to AccountInfo(ACCOUNT_TWO)
            )
        )

    val accounts: StateFlow<Map<String, AccountInfo>> =
        _accounts.asStateFlow()

    private fun updateAccount(
        id: String,
        transform: (AccountInfo) -> AccountInfo
    ) {
        val current = _accounts.value[id] ?: return
        _accounts.value = _accounts.value.toMutableMap().apply {
            put(id, transform(current))
        }
    }

    /**
     * راه‌اندازی یک حساب مستقل
     */
    fun startAccount(id: String): Boolean {
        if (id != ACCOUNT_ONE && id != ACCOUNT_TWO) return false

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

            val accountDirectory = File(
                appContext.filesDir,
                "telegram_accounts/$id"
            )

            accountDirectory.mkdirs()

            val client = TdClient(
                filesDir = accountDirectory.absolutePath,
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
                        if (update is TdApi.UpdateAuthorizationState) {
                            handleAuthorization(id, client, update)
                        }
                    }
                } catch (e: Exception) {
                    updateAccount(id) {
                        it.copy(status = "خطا در دریافت وضعیت اتصال")
                    }
                }
            }

            client.init()
            true

        } catch (e: Exception) {
            clients.remove(id)

            updateAccount(id) {
                it.copy(status = "خطا: ${e.message ?: "اتصال ناموفق"}")
            }

            false
        }
    }

    /**
     * بررسی وضعیت ورود حساب
     */
    private fun handleAuthorization(
        id: String,
        client: TdClient,
        update: TdApi.UpdateAuthorizationState
    ) {
        when (val state = update.authorizationState) {

            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                updateAccount(id) {
                    it.copy(status = "در حال آماده‌سازی...")
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                updateAccount(id) {
                    it.copy(status = "منتظر شماره تلفن")
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
                            ).filter { it.isNotBlank() }
                                .joinToString(" ")

                            updateAccount(id) {
                                it.copy(
                                    name = fullName.ifBlank {
                                        "حساب تلگرام"
                                    },
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

    /**
     * ارسال شماره تلفن
     */
    fun submitPhone(id: String, phone: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                client.send(
                    TdApi.SetAuthenticationPhoneNumber(
                        phone,
                        null
                    )
                )
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(status = "خطا در ارسال شماره")
                }
            }
        }
    }

    /**
     * ارسال کد تأیید
     */
    fun submitCode(id: String, code: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                client.send(
                    TdApi.CheckAuthenticationCode(code)
                )
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(status = "کد تأیید پذیرفته نشد")
                }
            }
        }
    }

    /**
     * ارسال رمز دومرحله‌ای
     */
    fun submitPassword(id: String, password: String) {
        val client = clients[id] ?: return

        scope.launch {
            try {
                client.send(
                    TdApi.CheckAuthenticationPassword(password)
                )
            } catch (e: Exception) {
                updateAccount(id) {
                    it.copy(status = "رمز واردشده صحیح نیست")
                }
            }
        }
    }

    /**
     * بستن یک نشست بدون بستن حساب دیگر
     */
    fun closeAccount(id: String) {
        clients.remove(id)?.close()

        updateAccount(id) {
            it.copy(
                status = "اتصال قطع شد",
                authorized = false
            )
        }
    }

    /**
     * آزادسازی منابع هنگام پایان کار مدیر
     */
    fun dispose() {
        clients.values.toList().forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }

        clients.clear()
        scope.cancel()
    }
}
