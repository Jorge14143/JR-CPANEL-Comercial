package ar.com.jrelectricidad.jrserver

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ServerState(val name:String,val online:Boolean,val level:String,val host:String,val cpu:Int?=null,val ram:Int?=null,val disk:Int?=null,val temp:String?=null,val uptime:Long?=null,val latency:Int?=null)
data class IncidentState(val event:String,val server:String,val title:String,val level:String,val time:String)
data class MonitorState(val level:String="loading",val cpu:Int=0,val ram:Int=0,val disk:Int=0,val temp:String="--",val alerts:Int=0,val servers:List<ServerState> = emptyList(),val error:String?=null)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);MobileApi.init(applicationContext);AlertNotifier.channel(applicationContext);MonitorScheduler.start(applicationContext)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),40)
  setContent{MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize()){JRServerApp()}}}
 }
}

@Composable
fun JRServerApp() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(MonitorState()) }
    var refreshing by remember { mutableStateOf(false) }
    var incidents by remember { mutableStateOf(listOf<IncidentState>()) }
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(MobileApi.hasToken()) }

    if (!paired) {
        PairScreen { paired = true }
        return
    }
    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onDisconnect = {
                MobileApi.disconnect()
                paired = false
                showSettings = false
            }
        )
        return
    }

    LaunchedEffect(Unit) {
        MobileApi.load { value ->
            (context as? android.app.Activity)?.runOnUiThread { state = value }
        }
        MobileApi.incidents { value ->
            (context as? android.app.Activity)?.runOnUiThread { incidents = value }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("◉") }, label = { Text("Estado") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("▣") }, label = { Text("Servidores") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("!") }, label = { Text("Alertas") })
            NavigationBarItem(selected = false, onClick = { showSettings = true }, icon = { Text("⚙") }, label = { Text("Ajustes") })
        }

        when (tab) {
            2 -> IncidentList(incidents)
            1 -> ServerList(state.servers)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("JR SERVER", style = MaterialTheme.typography.headlineMedium)
                            Text("Monitor móvil", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = {
                            refreshing = true
                            MobileApi.load { value ->
                                (context as? android.app.Activity)?.runOnUiThread {
                                    state = value
                                    refreshing = false
                                }
                            }
                            MobileApi.incidents { value ->
                                (context as? android.app.Activity)?.runOnUiThread { incidents = value }
                            }
                        }) {
                            Text(if (refreshing) "…" else "ACTUALIZAR")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                when (state.level) {
                                    "ok" -> "● TODO CORRECTO"
                                    "warning" -> "● ADVERTENCIA"
                                    "critical" -> "● FALLA DETECTADA"
                                    else -> "● CONECTANDO…"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("CPU", "${state.cpu}%", Modifier.weight(1f))
                        Metric("RAM", "${state.ram}%", Modifier.weight(1f))
                        Metric("DISCO", "${state.disk}%", Modifier.weight(1f))
                    }
                }
                item {
                    Metric("TEMPERATURA", if (state.temp == "--") "--" else "${state.temp} °C", Modifier.fillMaxWidth())
                }
                item { Text("SERVIDORES", style = MaterialTheme.typography.titleMedium) }
                items(state.servers.take(3)) { server -> ServerCard(server) }
                item { Text("Alertas activas: ${state.alerts}") }
            }
        }
    }
}

@Composable fun SettingsScreen(onBack:()->Unit,onDisconnect:()->Unit){
 var confirm by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().padding(24.dp)){
  TextButton(onClick=onBack){Text("← VOLVER")}
  Spacer(Modifier.height(12.dp));Text("AJUSTES",style=MaterialTheme.typography.headlineMedium)
  Spacer(Modifier.height(20.dp));Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Panel vinculado",style=MaterialTheme.typography.labelMedium);Text(MobileApi.panelUrl(),style=MaterialTheme.typography.bodyLarge);Spacer(Modifier.height(6.dp));Text("Monitor en segundo plano · cada 15 min",style=MaterialTheme.typography.bodySmall)}}
  Spacer(Modifier.height(16.dp));OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text("DESVINCULAR ESTE TELÉFONO")}
  if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Desvincular teléfono")},text={Text("La app dejará de consultar este JR Server Panel hasta que vuelvas a vincularla.")},confirmButton={TextButton(onClick=onDisconnect){Text("DESVINCULAR")}},dismissButton={TextButton(onClick={confirm=false}){Text("CANCELAR")}})
 }
}
@Composable fun PairScreen(done:()->Unit){
 val context=LocalContext.current
 var panel by remember{mutableStateOf(MobileApi.panelUrl())};var user by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
  Text("JR SERVER",style=MaterialTheme.typography.headlineLarge);Text("Vincular este teléfono",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(20.dp))
  OutlinedTextField(panel,{panel=it},label={Text("URL del panel")},placeholder={Text("https://panel.ejemplo.com")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp))
  OutlinedTextField(user,{user=it},label={Text("Usuario")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp))
  OutlinedTextField(pass,{pass=it},label={Text("Contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp))
  OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text("Código 2FA")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp))
  Button(onClick={busy=true;error=null;MobileApi.pair(panel,user,pass,code){ok,msg->(context as? android.app.Activity)?.runOnUiThread{busy=false;if(ok){MonitorScheduler.start(MobileApi.appContext());done()}else error=msg}}},enabled=!busy&&panel.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank()&&code.length==6,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Vinculando…" else "VINCULAR")}
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=10.dp))}
 }
}
@Composable fun ServerList(servers:List<ServerState>){
 LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("SERVIDORES",style=MaterialTheme.typography.titleLarge);Text("Estado del JR Server y Multi Servidor",style=MaterialTheme.typography.bodySmall)}
  items(servers){ServerCard(it)}
 }
}
@Composable fun ServerCard(s:ServerState){
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(s.name,style=MaterialTheme.typography.titleMedium);Text(s.host,style=MaterialTheme.typography.bodySmall)};Text(if(s.online)"● ONLINE" else "● OFFLINE",color=if(s.online)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
  if(s.online){Spacer(Modifier.height(10.dp));val parts=buildList{if(s.cpu!=null)add("CPU "+s.cpu+"%");if(s.ram!=null)add("RAM "+s.ram+"%");if(s.disk!=null)add("Disco "+s.disk+"%");if(s.temp!=null)add(s.temp+" °C");if(s.latency!=null)add(s.latency.toString()+" ms")};if(parts.isNotEmpty())Text(parts.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
  }
 }
}
@Composable fun IncidentList(items:List<IncidentState>){
 LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("ALERTAS E HISTORIAL",style=MaterialTheme.typography.titleLarge)}
  if(items.isEmpty())item{Card(Modifier.fillMaxWidth()){Text("Sin incidentes registrados",Modifier.padding(18.dp))}}
  items(items){x->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text((if(x.event=="resolved")"✓ RECUPERADO" else "● "+x.level.uppercase())+" · "+x.server,style=MaterialTheme.typography.titleSmall);Text(x.title);Text(x.time,style=MaterialTheme.typography.bodySmall)}}}
 }
}
@Composable fun Metric(label:String,value:String,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(14.dp)){Text(label,style=MaterialTheme.typography.labelSmall);Text(value,style=MaterialTheme.typography.titleLarge)}}}

object MobileApi{
 private const val DEFAULT_URL="https://jrserver.jrelectricidad.com.ar";private val client=OkHttpClient();private lateinit var context:Context
 fun init(c:Context){context=c.applicationContext};fun appContext()=context
 private fun prefs()=context.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE)
 fun panelUrl()=(prefs().getString("panel_url",DEFAULT_URL)?:DEFAULT_URL).trimEnd('/')
 private fun normalizeUrl(v:String):String{var x=v.trim().trimEnd('/');if(!x.startsWith("https://")&&!x.startsWith("http://"))x="https://"+x;require(x.startsWith("https://")){"JR Server Mobile requiere HTTPS"};return x}
 private fun token()=prefs().getString("token","")?:"";fun hasToken()=token().isNotBlank()
 private fun saveToken(value:String){prefs().edit().putString("token",value).apply()}
 fun disconnect(){prefs().edit().remove("token").remove("last_level").remove("active_incidents").apply();WorkManager.getInstance(context).cancelUniqueWork("jr-monitor")}
 fun pair(panel:String,user:String,pass:String,code:String,done:(Boolean,String)->Unit){
  val json=JSONObject().put("username",user).put("password",pass).put("code",code).put("deviceName",Build.MANUFACTURER+" "+Build.MODEL).toString()
  val base=try{normalizeUrl(panel)}catch(e:Exception){done(false,e.message?:"URL inválida");return};val req=Request.Builder().url(base+"/api/mobile/v1/pair").post(json.toRequestBody("application/json".toMediaType())).build()
  client.newCall(req).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=done(false,"No se pudo conectar");override fun onResponse(c:Call,r:Response){r.use{try{val j=JSONObject(it.body?.string()?:"{}");if(it.isSuccessful&&j.optString("token").isNotBlank()){prefs().edit().putString("panel_url",base).apply();saveToken(j.getString("token"));done(true,"")}else done(false,"Credenciales o 2FA incorrectos")}catch(e:Exception){done(false,"Respuesta no válida")}}}})
 }
 fun statusJson(done:(JSONObject?)->Unit){val b=Request.Builder().url(panelUrl()+"/api/mobile/v1/status");val t=token();if(t.isNotBlank())b.header("Authorization","Bearer "+t);client.newCall(b.build()).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=done(null);override fun onResponse(c:Call,r:Response){r.use{done(if(it.isSuccessful)runCatching{JSONObject(it.body?.string()?:"{}")}.getOrNull() else null)}}})}
 fun incidents(done:(List<IncidentState>)->Unit){val b=Request.Builder().url(panelUrl()+"/api/mobile/v1/incidents?limit=100");val t=token();if(t.isNotBlank())b.header("Authorization","Bearer "+t);client.newCall(b.build()).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=done(emptyList());override fun onResponse(c:Call,r:Response){r.use{try{val j=JSONObject(it.body?.string()?:"{}");val a=j.optJSONArray("history");val out=buildList{if(a!=null)for(i in a.length()-1 downTo 0){val x=a.getJSONObject(i);add(IncidentState(x.optString("event"),x.optString("server"),x.optString("title"),x.optString("level"),x.optString("time")))}};done(out)}catch(e:Exception){done(emptyList())}}}})}
 fun load(done:(MonitorState)->Unit)=statusJson{j->if(j==null){done(MonitorState(level="critical",error="No se pudo consultar el panel"));return@statusJson};try{val summary=j.getJSONObject("summary");val local=j.getJSONObject("local");val arr=j.getJSONArray("servers");val servers=buildList{for(i in 0 until arr.length()){val x=arr.getJSONObject(i);add(ServerState(x.optString("name"),x.optBoolean("online"),x.optString("level"),x.optString("host"),if(x.has("cpuPercent"))x.optInt("cpuPercent") else null,if(x.has("memoryPercent"))x.optInt("memoryPercent") else null,if(x.has("diskPercent"))x.optInt("diskPercent") else null,if(x.has("temperature")&&!x.isNull("temperature"))x.optDouble("temperature").toString() else null,if(x.has("uptime"))x.optLong("uptime") else null,if(x.has("latencyMs")&&!x.isNull("latencyMs"))x.optInt("latencyMs") else null))}};done(MonitorState(summary.optString("level"),local.optInt("cpuPercent"),local.optInt("memoryPercent"),local.optInt("diskPercent"),if(local.isNull("temperature"))"--" else local.optDouble("temperature").toString(),summary.optInt("alertCount"),servers))}catch(e:Exception){done(MonitorState(level="critical",error="Respuesta no válida"))}}
}

object MonitorScheduler{fun start(c:Context){val req=PeriodicWorkRequestBuilder<MonitorWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("jr-monitor",ExistingPeriodicWorkPolicy.UPDATE,req)}}
class MonitorWorker(c:Context,p:WorkerParameters):Worker(c,p){
 override fun doWork():Result{MobileApi.init(applicationContext);if(!MobileApi.hasToken())return Result.success();val latch=java.util.concurrent.CountDownLatch(1);var ok=false;MobileApi.statusJson{j->if(j!=null){AlertNotifier.evaluate(applicationContext,j);ok=true};latch.countDown()};latch.await(20,TimeUnit.SECONDS);return if(ok)Result.success() else Result.retry()}
}
object AlertNotifier{
 private const val CH="jr_alerts"
 fun channel(c:Context){if(Build.VERSION.SDK_INT>=26)(c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CH,"Alertas JR Server",NotificationManager.IMPORTANCE_HIGH))}
 fun evaluate(c:Context,j:JSONObject){
  val prefs=c.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE)
  val active=j.optJSONArray("incidents")
  val now=mutableSetOf<String>();val info=mutableMapOf<String,Pair<String,String>>()
  if(active!=null)for(i in 0 until active.length()){val x=active.optJSONObject(i)?:continue;val key=x.optString("key");if(key.isNotBlank()){now.add(key);info[key]=Pair(x.optString("server","JR Server"),x.optString("title","Falla detectada"))}}
  val before=prefs.getStringSet("active_incidents",emptySet())?.toSet()?:emptySet()
  for(key in now-before){val x=info[key];notify(c,key.hashCode(),"JR Server · "+(x?.first?:"Alerta"),x?.second?:"Se detectó un nuevo incidente.")}
  for(key in before-now)notify(c,key.hashCode(),"JR Server · Recuperado","Un incidente activo fue resuelto. Abrí la app para ver el historial.")
  prefs.edit().putStringSet("active_incidents",now).apply()
 }
 private fun notify(c:Context,id:Int,title:String,text:String){if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;channel(c);val intent=Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP);val pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=NotificationCompat.Builder(c,CH).setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).setAutoCancel(true).build();NotificationManagerCompat.from(c).notify(id,n)}
}
