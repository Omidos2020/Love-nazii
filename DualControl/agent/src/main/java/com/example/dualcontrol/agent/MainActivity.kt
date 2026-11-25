package com.example.dualcontrol.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : Activity() {
 private lateinit var pin: String
 private lateinit var info: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("dual_control", MODE_PRIVATE)
  pin = prefs.getString("pairing_pin", null) ?: (100000..999999).random().toString().also {
   prefs.edit().putString("pairing_pin", it).apply()
  }
  val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,48,36,36) }
  layout.addView(TextView(this).apply { text="Dual Control Agent"; textSize=24f })
  info = TextView(this).apply { textSize=17f }
  layout.addView(info)
  layout.addView(Button(this).apply { text="شروع سرویس اتصال"; setOnClickListener {
   startForegroundService(Intent(this@MainActivity, AgentService::class.java).putExtra("pin",pin))
   Toast.makeText(this@MainActivity,"سرویس فعال شد؛ اعلان آن قابل مشاهده است",Toast.LENGTH_LONG).show()
  }})
  layout.addView(Button(this).apply { text="توقف سرویس"; setOnClickListener { stopService(Intent(this@MainActivity,AgentService::class.java)) }})
  setContentView(layout); refresh()
 }
 private fun refresh() {
  info.text="کد اتصال: $pin\nIP گوشی در شبکه Wi-Fi: ${localIp()}\nدر گوشی دوم IP و کد را وارد کنید."
 }
 private fun localIp(): String = try {
  Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
   .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':')==false }?.hostAddress ?: "IP پیدا نشد"
 } catch (_:Exception) { "IP در دسترس نیست" }
}
