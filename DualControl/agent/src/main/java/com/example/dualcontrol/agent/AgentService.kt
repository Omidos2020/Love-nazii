package com.example.dualcontrol.agent

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import kotlin.concurrent.thread

class AgentService : Service() {
 @Volatile private var running=false
 private var server: ServerSocket?=null
 private var pin="000000"
 private val maxUploadBytes=25L*1024L*1024L

 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  pin=intent?.getStringExtra("pin") ?: pin
  if(Build.VERSION.SDK_INT>=26) {
   val nm=getSystemService(NotificationManager::class.java)
   nm.createNotificationChannel(NotificationChannel("dual_control","وضعیت Dual Control",NotificationManager.IMPORTANCE_LOW))
  }
  val notification=if(Build.VERSION.SDK_INT>=26)
   Notification.Builder(this,"dual_control").setContentTitle("Dual Control Agent فعال است").setContentText("اتصال محلی و دریافت فایل فعال است؛ برای توقف برنامه را باز کنید.").setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).build()
  else Notification.Builder(this).setContentTitle("Dual Control Agent فعال است").setSmallIcon(android.R.drawable.stat_sys_upload_done).build()
  startForeground(7,notification)
  if(!running) startServer()
  return START_STICKY
 }

 private fun startServer() {
  running=true
  thread(name="DualControlServer") {
   try {
    server=ServerSocket(8765)
    while(running) {
     val socket=server!!.accept()
     thread(name="DualControlClient") { handle(socket) }
    }
   } catch(_:Exception) { } finally { running=false }
  }
 }

 private fun readLine(input:InputStream):String {
  val bytes=java.io.ByteArrayOutputStream()
  while(true) {
   val b=input.read()
   if(b<0 || b==10) break
   if(b!=13) bytes.write(b)
   if(bytes.size()>8192) throw IllegalArgumentException("Header too long")
  }
  return bytes.toString(Charsets.ISO_8859_1.name())
 }

 private fun respond(socket:Socket,status:String,body:String) {
  val bytes=body.toByteArray(Charsets.UTF_8)
  val out=socket.getOutputStream()
  out.write(("HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
  out.write(bytes); out.flush()
 }

 private fun handle(socket:Socket) {
  socket.use { s ->
   try {
    s.soTimeout=15000
    val input=s.getInputStream()
    val request=readLine(input).split(" ")
    if(request.size<2) { respond(s,"400 Bad Request","{\"error\":\"bad_request\"}"); return }
    val method=request[0].uppercase(Locale.US)
    val target=request[1]
    val headers=mutableMapOf<String,String>()
    while(true) {
     val line=readLine(input)
     if(line.isEmpty()) break
     val split=line.indexOf(':')
     if(split>0) headers[line.substring(0,split).trim().lowercase(Locale.US)]=line.substring(split+1).trim()
    }
    val path=target.substringBefore('?')
    val query=target.substringAfter('?', "")
    val suppliedPin=query.split('&').firstOrNull { it.startsWith("pin=") }?.substringAfter("pin=")?.let { URLDecoder.decode(it,"UTF-8") }
    if(suppliedPin!=pin) { respond(s,"403 Forbidden","{\"error\":\"invalid_pin\"}"); return }
    if(method=="GET" && path=="/status") {
     respond(s,"200 OK","{\"connected\":true,\"device\":\"Dual Control Agent\",\"fileTransfer\":true}")
     return
    }
    if(method=="POST" && path=="/upload") {
     val length=headers["content-length"]?.toLongOrNull() ?: 0L
     if(length<=0 || length>maxUploadBytes) { respond(s,"413 Payload Too Large","{\"error\":\"file_size_limit_25mb\"}"); return }
     val rawName=headers["x-file-name"] ?: "received_file"
     val fileName=URLDecoder.decode(rawName,"UTF-8").substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "received_file" }
     val mime=headers["content-type"] ?: "application/octet-stream"
     val values=ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME,fileName)
      put(MediaStore.Downloads.MIME_TYPE,mime)
      if(Build.VERSION.SDK_INT>=29) put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/DualControl")
     }
     val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
     if(uri==null) { respond(s,"500 Internal Server Error","{\"error\":\"storage_unavailable\"}"); return }
     try {
      contentResolver.openOutputStream(uri)?.use { output ->
       val buffer=ByteArray(16*1024)
       var remaining=length
       while(remaining>0) {
        val count=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
        if(count<0) throw java.io.EOFException("Upload ended early")
        output.write(buffer,0,count); remaining-=count
       }
      } ?: throw IllegalStateException("Cannot open output")
     } catch(e:Exception) {
      contentResolver.delete(uri,null,null)
      throw e
     }
     respond(s,"200 OK","{\"uploaded\":true,\"file\":\"$fileName\",\"bytes\":$length}")
     return
    }
    respond(s,"404 Not Found","{\"error\":\"not_found\"}")
   } catch(_:Exception) {
    try { respond(s,"400 Bad Request","{\"error\":\"request_failed\"}") } catch(_:Exception) {}
   }
  }
 }

 override fun onDestroy() { running=false; try { server?.close() } catch(_:Exception){}; stopForeground(true); super.onDestroy() }
 override fun onBind(intent:Intent?):IBinder?=null
}
