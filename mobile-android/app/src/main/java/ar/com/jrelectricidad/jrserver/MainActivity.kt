package ar.com.jrelectricidad.jrserver

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

data class ServerState(val name:String,val online:Boolean,val level:String,val host:String)
data class MonitorState(val level:String="loading",val cpu:Int=0,val ram:Int=0,val disk:Int=0,val temp:String="--",val alerts:Int=0,val servers:List<ServerState> = emptyList(),val error:String?=null)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);MobileApi.init(applicationContext);setContent{MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize()){JRServerApp()}}}}
}

@Composable fun JRServerApp(){
 var state by remember{mutableStateOf(MonitorState())}
 LaunchedEffect(Unit){MobileApi.load{state=it}}
 LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("JR SERVER",style=MaterialTheme.typography.headlineMedium);Text("Monitor móvil",color=MaterialTheme.colorScheme.primary)}
  item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){
   Text(when(state.level){"ok"->"● TODO CORRECTO";"warning"->"● ADVERTENCIA";"critical"->"● FALLA DETECTADA";else->"● CONECTANDO…"},style=MaterialTheme.typography.titleLarge)
   state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  }}}
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("CPU",state.cpu.toString()+"%",Modifier.weight(1f));Metric("RAM",state.ram.toString()+"%",Modifier.weight(1f));Metric("DISCO",state.disk.toString()+"%",Modifier.weight(1f))}}
  item{Metric("TEMPERATURA",if(state.temp=="--")"--" else state.temp+" °C",Modifier.fillMaxWidth())}
  item{Text("SERVIDORES",style=MaterialTheme.typography.titleMedium)}
  items(state.servers){s->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text(s.name);Text(s.host,style=MaterialTheme.typography.bodySmall)}
   Text(if(s.online)"● ONLINE" else "● OFFLINE",color=if(s.online)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
  }}}
  item{Text("Alertas activas: "+state.alerts)}
 }
}
@Composable fun Metric(label:String,value:String,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(14.dp)){Text(label,style=MaterialTheme.typography.labelSmall);Text(value,style=MaterialTheme.typography.titleLarge)}}}

object MobileApi{
 private const val BASE_URL="https://jrserver.jrelectricidad.com.ar"
 private val client=OkHttpClient()
 private lateinit var context:Context
 fun init(c:Context){context=c.applicationContext}
 private fun token():String=context.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE).getString("token","")?:""
 fun saveToken(value:String){context.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE).edit().putString("token",value).apply()}
 fun load(done:(MonitorState)->Unit){
  val builder=Request.Builder().url(BASE_URL+"/api/mobile/v1/status");val t=token();if(t.isNotBlank())builder.header("Authorization","Bearer "+t);val req=builder.build()
  client.newCall(req).enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException)=done(MonitorState(level="critical",error="No se pudo conectar al panel"))
   override fun onResponse(call:Call,response:Response){response.use{
    if(!it.isSuccessful){done(MonitorState(level="critical",error=if(it.code==401)"Dispositivo sin vincular" else "Panel respondió HTTP "+it.code));return}
    try{
     val j=JSONObject(it.body?.string()?:"{}");val summary=j.getJSONObject("summary");val local=j.getJSONObject("local");val arr=j.getJSONArray("servers")
     val servers=buildList{for(i in 0 until arr.length()){val x=arr.getJSONObject(i);add(ServerState(x.optString("name"),x.optBoolean("online"),x.optString("level"),x.optString("host")))}}
     done(MonitorState(summary.optString("level"),local.optInt("cpuPercent"),local.optInt("memoryPercent"),local.optInt("diskPercent"),if(local.isNull("temperature"))"--" else local.optDouble("temperature").toString(),summary.optInt("alertCount"),servers))
    }catch(e:Exception){done(MonitorState(level="critical",error="Respuesta del panel no válida"))}
   }}
  })
 }
}
