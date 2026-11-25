package com.example.dualcontrol.controller

import android.app.Activity
import android.os.Bundle
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,32) }
  layout.addView(TextView(this).apply { text="Dual Control"; textSize=26f })
  layout.addView(TextView(this).apply { text="IP گوشی اول را وارد کنید" })
  val ip=EditText(this).apply { hint="مثلاً 192.168.43.120"; setSingleLine(true) }
  val pin=EditText(this).apply { hint="کد اتصال ۶ رقمی"; setSingleLine(true) }
  val result=TextView(this).apply { text="هنوز متصل نشده‌اید"; textSize=16f }
  val button=Button(this).apply { text="بررسی اتصال" }
  layout.addView(ip); layout.addView(pin); layout.addView(button); layout.addView(result); setContentView(layout)
  button.setOnClickListener {
   button.isEnabled=false; result.text="در حال بررسی..."
   thread {
    val message=try {
     val conn=URL("http://${ip.text.toString().trim()}:8765/status?pin=${pin.text.toString().trim()}").openConnection() as HttpURLConnection
     conn.connectTimeout=4000; conn.readTimeout=4000
     val response=conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
     if(response.contains("\"connected\":true")) "اتصال محلی برقرار است." else "پاسخ نامعتبر از دستگاه."
    } catch(_:Exception) { "اتصال برقرار نشد؛ IP، کد و اتصال Wi-Fi را بررسی کنید." }
    runOnUiThread { result.text=message; button.isEnabled=true }
   }
  }
 }
}
