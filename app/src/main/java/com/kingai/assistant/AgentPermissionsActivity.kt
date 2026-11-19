package com.kingai.assistant

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import androidx.activity.ComponentActivity

/** مجوزهای Agent جداگانه و با حالت خاموش پیش‌فرض مدیریت می‌شوند. */
class AgentPermissionsActivity : ComponentActivity() {
 private lateinit var host:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);applyNeonWindow();val (root,content)=neonScreen(this);content.addView(topBar(this,"مجوزهای دستیار KING AI"),vlp(0));content.addView(label(this,"هر قابلیت را جداگانه روشن کن. روشن‌کردن مجوز جایگزین تأیید همان عملیات نیست؛ ارسال، حذف، ویرایش و فوروارد همیشه تأیید تازه می‌خواهند.",13f,Neon.TEXT_DIM),vlp(12));host=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };content.addView(host,vlp(0));setContentView(root);render() }
 override fun onResume(){super.onResume();if(::host.isInitialized)render()}
 private fun render(){host.removeAllViews();val engine=AgentEngine(this);AgentPermission.values().forEach { p -> val card=neonCard(this);val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL };val texts=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };texts.addView(label(this,p.title,15f,Neon.GREEN,true));texts.addView(label(this,p.detail,12f,Neon.TEXT_DIM),vlp(5));row.addView(texts,LinearLayout.LayoutParams(0,-2,1f));val toggle=Switch(this).apply { isChecked=engine.isEnabled(p);text=if(isChecked) "روشن" else "خاموش";setOnCheckedChangeListener { _,checked -> engine.setEnabled(p,checked);text=if(checked) "روشن" else "خاموش" } };row.addView(toggle);card.addView(row);if(p.risk==AgentRisk.SENSITIVE || p.risk==AgentRisk.DESTRUCTIVE) card.addView(label(this,"توجه: فعال‌بودن مجوز، تأیید همان عملیات را حذف نمی‌کند.",11f,Neon.TEXT_DIM),vlp(8));host.addView(card,vlp(10)) };val traceCard=neonCard(this);traceCard.addView(label(this,"گزارش فعالیت‌های Agent",15f,Neon.GREEN,true));val trace=engine.traceSnapshot().takeLast(12).asReversed();traceCard.addView(label(this,if(trace.isEmpty()) "هنوز فعالیتی ثبت نشده است." else trace.joinToString("\n"),11f,Neon.TEXT_DIM),vlp(8));host.addView(traceCard,vlp(12))}
}
