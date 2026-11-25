package com.example.dualcontrol.controller

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private lateinit var ip:EditText
 private lateinit var pin:EditText
 private lateinit var result:TextView
 private var selectedFile:Uri?=null
 private var selectedName=""
 private lateinit var sendButton:Button

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,32) }
  layout.addView(TextView(this).apply { text="Dual Control"; textSize=26f })
  layout.addView(TextView(this).apply { text="IP گوشی اول را وارد کنید" })
  ip=EditText(this).apply { hint="مثلاً 192.168.43.120"; setSingleLine(true) }
  pin=EditText(this).apply { hint="کد اتصال ۶ رقمی"; setSingleLine(true) }
  result=TextView(this).apply { text="برای شروع، هر دو گوشی باید به یک شبکه Wi-Fi وصل باشند."; textSize=16f }
  val connect=Button(this).apply { text="بررسی اتصال" }
  val choose=Button(this).apply { text="انتخاب فایل از این گوشی" }
  sendButton=Button(this).apply { text="ارسال فایل به گوشی اول"; isEnabled=false }
  layout.addView(ip); layout.addView(pin); layout.addView(connect); layout.addView(choose); layout.addView(sendButton); layout.addView(result)
  setContentView(ScrollView(this).apply { addView(layout) })

  connect.setOnClickListener { runRequest(connect,"در حال بررسی اتصال...") {
   val conn=URL(endpoint("/status")).openConnection() as HttpURLConnection
   conn.connectTimeout=5000; conn.readTimeout=5000
   val response=conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
   if(response.contains("\"connected\":true")) "اتصال برقرار است." else "پاسخ نامعتبر از گوشی اول."
  } }
  choose.setOnClickListener {
   val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
   startActivityForResult(intent,41)
  }
  sendButton.setOnClickListener {
   val uri=selectedFile ?: return@setOnClickListener
   runRequest(sendButton,"در حال ارسال فایل...") {
    val mime=contentResolver.getType(uri) ?: "application/octet-stream"
    val connection=URL(endpoint("/upload")).openConnection() as HttpURLConnection
    connection.connectTimeout=10000; connection.readTimeout=30000
    connection.requestMethod="POST"; connection.doOutput=true
    connection.setRequestProperty("Content-Type",mime)
    connection.setRequestProperty("X-File-Name",URLEncoder.encode(selectedName,"UTF-8"))
    val temp=java.io.File.createTempFile("dualcontrol_upload_",".bin",cacheDir)
    try {
     contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
      ?: throw IllegalStateException("فایل قابل خواندن نیست")
     val length=temp.length()
     require(length in 1..(25L*1024L*1024L)) { "حجم فایل باید بین ۱ بایت تا ۲۵ مگابایت باشد." }
     connection.setFixedLengthStreamingMode(length)
     temp.inputStream().use { input ->
      connection.outputStream.use { output ->
       val buffer=ByteArray(16*1024)
       while(true) { val n=input.read(buffer); if(n<0) break; output.write(buffer,0,n) }
      }
     }
    } finally { temp.delete() }
    val code=connection.responseCode
    val stream=if(code in 200..299) connection.inputStream else connection.errorStream
    val response=stream?.bufferedReader()?.use { it.readText() } ?: ""
    connection.disconnect()
    if(code in 200..299) "فایل با موفقیت به پوشه Downloads/DualControl گوشی اول فرستاده شد." else "ارسال ناموفق بود: $response"
   }
  }
 }

 private fun endpoint(path:String):String {
  val address=ip.text.toString().trim()
  val code=pin.text.toString().trim()
  require(address.isNotBlank() && code.length==6) { "IP و کد اتصال ۶ رقمی را وارد کنید." }
  return "http://$address:8765$path?pin=${URLEncoder.encode(code,"UTF-8")}"
 }

 private fun runRequest(button:Button,loading:String,work:()->String) {
  button.isEnabled=false; result.text=loading
  thread {
   val message=try { work() } catch(e:Exception) { e.message ?: "اتصال برقرار نشد؛ IP، کد و شبکه را بررسی کنید." }
   runOnUiThread { result.text=message; button.isEnabled=true }
  }
 }

 @Deprecated("Deprecated in Android")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==41 && resultCode==RESULT_OK) {
   selectedFile=data?.data ?: return
   selectedName=selectedFile?.let { uri ->
    contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
     if(cursor.moveToFirst()) cursor.getString(0) else "received_file"
    }
   } ?: "received_file"
   findSendButtonEnable()
   result.text="فایل انتخاب شد: $selectedName"
  }
 }
 private fun findSendButtonEnable() { sendButton.isEnabled=true }
}
