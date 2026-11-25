package com.example.dualcontrol.agent

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.net.ServerSocket
import kotlin.concurrent.thread

class AgentService : Service() {
 @Volatile private var running=false
 private var server: ServerSocket?=null
 private var pin="000000"
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  pin=intent?.getStringExtra("pin") ?: pin
  if(Build.VERSION.SDK_INT>=26) {
   val nm=getSystemService(NotificationManager::class.java)
   nm.createNotificationChannel(NotificationChannel("dual_control","وضعیت Dual Control",NotificationManager.IMPORTANCE_LOW))
  }
  val notification=if(Build.VERSION.SDK_INT>=26)
   Notification.Builder(this,"dual_control").setContentTitle("Dual Control Agent فعال است").setContentText("اتصال محلی آماده است؛ برای توقف برنامه را باز کنید.").setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).build()
  else Notification.Builder(this).setContentTitle("Dual Control Agent فعال است").setSmallIcon(android.R.drawable.stat_sys_upload_done).build()
  startForeground(7,notification)
  if(!running) startServer()
  return START_STICKY
 }
 private fun startServer() {
  running=true
  thread {
   try {
    server=ServerSocket(8765)
    while(running) {
     val socket=server!!.accept()
     thread {
      socket.use { s ->
       s.soTimeout=5000
       val reader=s.getInputStream().bufferedReader()
       val request=reader.readLine() ?: ""
       while(true) { val line=reader.readLine() ?: break; if(line.isEmpty()) break }
       val path=request.split(" ").getOrNull(1) ?: "/"
       val ok=path=="/status?pin=$pin"
       val body=if(ok) "{\"connected\":true,\"device\":\"Dual Control Agent\"}" else "{\"connected\":false}"
       val bytes=body.toByteArray()
       val out=s.getOutputStream()
       out.write(("HTTP/1.1 "+(if(ok) "200 OK" else "403 Forbidden")+"\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
       out.write(bytes); out.flush()
      }
     }
    }
   } catch(_:Exception) { } finally { running=false }
  }
 }
 override fun onDestroy() { running=false; try { server?.close() } catch(_:Exception){}; stopForeground(true); super.onDestroy() }
 override fun onBind(intent:Intent?):IBinder?=null
}
