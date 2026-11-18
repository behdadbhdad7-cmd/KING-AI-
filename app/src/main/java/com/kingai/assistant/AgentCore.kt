package com.kingai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class AgentRisk { READ_ONLY, REVERSIBLE, SENSITIVE, DESTRUCTIVE }
enum class AgentPermission(val title: String, val detail: String, val risk: AgentRisk) {
 READ_TELEGRAM("خواندن پیام‌های تلگرام","مشاهده و خلاصه‌سازی پیام‌های حساب انتخاب‌شده.",AgentRisk.READ_ONLY),
 SEARCH_TELEGRAM("جست‌وجوی گفتگوها","جست‌وجو در گفتگوهای حساب انتخاب‌شده.",AgentRisk.READ_ONLY),
 DRAFT_REPLY("پیشنهاد پاسخ","ساخت پیش‌نویس بدون ارسال خودکار.",AgentRisk.READ_ONLY),
 SEND_MESSAGE("ارسال پیام","ارسال فقط با تأیید تازه برای متن و مقصد دقیق.",AgentRisk.SENSITIVE),
 EDIT_MESSAGE("ویرایش پیام","تغییر پیام فقط پس از نمایش متن جدید و تأیید.",AgentRisk.SENSITIVE),
 DELETE_MESSAGE("حذف پیام","حذف فقط پس از نمایش پیام هدف و تأیید.",AgentRisk.DESTRUCTIVE),
 FORWARD_MESSAGE("فوروارد پیام","فوروارد فقط پس از نمایش پیام و مقصد و تأیید.",AgentRisk.SENSITIVE),
 JOIN_LEAVE_CHAT("عضویت یا خروج از گفتگو","تغییر عضویت فقط با تأیید تازه.",AgentRisk.SENSITIVE),
 PUBLISH_CONTENT("انتشار محتوا","انتشار فقط با تأیید متن و مقصد.",AgentRisk.SENSITIVE),
 SCHEDULE_READ_ONLY("زمان‌بندی کارهای فقط‌خواندنی","بررسی و خلاصه‌سازی زمان‌بندی‌شده، بدون ارسال یا حذف.",AgentRisk.READ_ONLY)
}
enum class AgentActionType(val permission: AgentPermission,val risk: AgentRisk) {
 READ_RECENT_MESSAGES(AgentPermission.READ_TELEGRAM,AgentRisk.READ_ONLY),
 SEARCH_CHATS(AgentPermission.SEARCH_TELEGRAM,AgentRisk.READ_ONLY),
 DRAFT_REPLY(AgentPermission.DRAFT_REPLY,AgentRisk.READ_ONLY),
 SEND_MESSAGE(AgentPermission.SEND_MESSAGE,AgentRisk.SENSITIVE),
 EDIT_MESSAGE(AgentPermission.EDIT_MESSAGE,AgentRisk.SENSITIVE),
 DELETE_MESSAGE(AgentPermission.DELETE_MESSAGE,AgentRisk.DESTRUCTIVE),
 FORWARD_MESSAGE(AgentPermission.FORWARD_MESSAGE,AgentRisk.SENSITIVE),
 JOIN_CHAT(AgentPermission.JOIN_LEAVE_CHAT,AgentRisk.SENSITIVE),
 LEAVE_CHAT(AgentPermission.JOIN_LEAVE_CHAT,AgentRisk.SENSITIVE),
 PUBLISH_MESSAGE(AgentPermission.PUBLISH_CONTENT,AgentRisk.SENSITIVE)
}
data class AgentAction(val type:AgentActionType,val accountId:String,val chatId:Long=0L,val targetChatId:Long=0L,val messageId:Long=0L,val text:String="",val summary:String="") {
 fun canonical()=JSONObject().apply { put("type",type.name);put("accountId",accountId);put("chatId",chatId);put("targetChatId",targetChatId);put("messageId",messageId);put("text",text);put("summary",summary) }.toString()
 fun fingerprint()=MessageDigest.getInstance("SHA-256").digest(canonical().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
data class AgentPlan(val goal:String,val steps:List<AgentAction>) {
 companion object {
  const val MAX_STEPS=8
  fun parse(raw:String):AgentPlan {
   val root=JSONObject(raw);val goal=root.optString("goal").trim()
   require(goal.isNotBlank() && goal.length<=500) { "هدف برنامه نامعتبر است." }
   val array=root.optJSONArray("steps") ?: error("فهرست مراحل وجود ندارد.")
   require(array.length() in 1..MAX_STEPS) { "تعداد مراحل باید بین ۱ تا ۸ باشد." }
   val steps=ArrayList<AgentAction>()
   for(i in 0 until array.length()) {
    val item=array.optJSONObject(i) ?: error("مرحله ساختار معتبری ندارد.")
    val type=runCatching { AgentActionType.valueOf(item.optString("tool")) }.getOrElse { error("ابزار ناشناخته است؛ اجرای آن مجاز نیست.") }
    val account=item.optString("account_id").trim()
    require(account=="account1" || account=="account2") { "حساب تلگرام معتبر نیست." }
    val body=item.optString("text");val summary=item.optString("summary")
    require(body.length<=4000 && summary.length<=1000) { "متن مرحله بیش از حد طولانی است." }
    steps += AgentAction(type,account,item.optLong("chat_id"),item.optLong("target_chat_id"),item.optLong("message_id"),body,summary)
   }
   return AgentPlan(goal,steps)
  }
 }
}
data class AgentApproval(val id:String,val fingerprint:String,val expiresAt:Long)
data class AgentToolResult(val success:Boolean,val message:String)
interface AgentTool { val type:AgentActionType; suspend fun execute(action:AgentAction):AgentToolResult }

/** Central permission, one-time approval, allowlist and trace boundary for agent tools. */
class AgentEngine(context:Context,tools:List<AgentTool> = emptyList()) {
 private val prefs=context.applicationContext.getSharedPreferences("king_agent_security_v1",Context.MODE_PRIVATE)
 private val registry=tools.associateBy { it.type }
 fun isEnabled(permission:AgentPermission)=prefs.getBoolean("permission_"+permission.name,false)
 fun setEnabled(permission:AgentPermission,enabled:Boolean) { prefs.edit().putBoolean("permission_"+permission.name,enabled).apply();trace("permission",permission.name+"="+enabled) }
 fun issueApproval(action:AgentAction,ttlMillis:Long=120000L):AgentApproval {
  require(action.type.risk==AgentRisk.SENSITIVE || action.type.risk==AgentRisk.DESTRUCTIVE)
  val approval=AgentApproval(UUID.randomUUID().toString(),action.fingerprint(),System.currentTimeMillis()+ttlMillis.coerceIn(1000L,120000L))
  prefs.edit().putString("approval_"+approval.id,JSONObject().put("fingerprint",approval.fingerprint).put("expires",approval.expiresAt).toString()).apply()
  trace("approval_created",action.type.name+"/"+action.accountId+"/"+action.chatId);return approval
 }
 private fun consumeApproval(action:AgentAction,approvalId:String?):Boolean {
  if(approvalId.isNullOrBlank()) return false
  val key="approval_"+approvalId;val raw=prefs.getString(key,null) ?: return false
  prefs.edit().remove(key).apply()
  val saved=runCatching { JSONObject(raw) }.getOrNull() ?: return false
  val valid=saved.optString("fingerprint")==action.fingerprint() && saved.optLong("expires")>=System.currentTimeMillis()
  trace("approval_consumed",action.type.name+" valid="+valid);return valid
 }
 suspend fun execute(action:AgentAction,approvalId:String?=null):AgentToolResult {
  if(!isEnabled(action.type.permission)) { trace("blocked","permission_off:"+action.type.permission.name);return AgentToolResult(false,"مجوز این قابلیت خاموش است.") }
  if(action.type.risk==AgentRisk.SENSITIVE || action.type.risk==AgentRisk.DESTRUCTIVE) {
   if(!consumeApproval(action,approvalId)) { trace("blocked","approval_missing_or_invalid:"+action.type.name);return AgentToolResult(false,"برای این عملیات تأیید تازه و منطبق لازم است.") }
  }
  val tool=registry[action.type] ?: return AgentToolResult(false,"ابزار "+action.type.name+" هنوز به سرویس اجرایی متصل نشده است.")
  return try { val result=tool.execute(action);trace(if(result.success) "tool_success" else "tool_failed",action.type.name+": "+result.message.take(300));result }
  catch(error:Exception) { trace("tool_exception",action.type.name+": "+error.javaClass.simpleName);AgentToolResult(false,"اجرای ابزار با خطا متوقف شد.") }
 }
 fun traceSnapshot():List<String> = runCatching { val a=JSONArray(prefs.getString("trace","[]") ?: "[]");(0 until a.length()).map { a.optJSONObject(it)?.optString("entry").orEmpty() }.filter { it.isNotBlank() } }.getOrDefault(emptyList())
 private fun trace(kind:String,detail:String) { runCatching { val a=JSONArray(prefs.getString("trace","[]") ?: "[]");a.put(JSONObject().put("entry",System.currentTimeMillis().toString()+" | "+kind+" | "+detail.take(400)));val out=JSONArray();for(i in maxOf(0,a.length()-100) until a.length()) out.put(a.get(i));prefs.edit().putString("trace",out.toString()).apply() } }
}
