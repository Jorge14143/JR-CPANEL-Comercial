package ar.com.jrelectricidad.jrserver

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max

private val Navy=Color(0xFF020B16)
private val Panel=Color(0xFF071A2C)
private val Panel2=Color(0xFF0A2842)
private val Blue=Color(0xFF009DFF)
private val Cyan=Color(0xFF00E5FF)
private val Green=Color(0xFF00F59B)
private val Orange=Color(0xFFFF9D19)
private val Purple=Color(0xFFC04CFF)
private val Red=Color(0xFFFF365F)
private val Muted=Color(0xFFA9BED3)

data class ServerState(val id:String,val name:String,val online:Boolean,val level:String,val host:String,val cpu:Int?=null,val ram:Int?=null,val disk:Int?=null,val temp:String?=null,val uptime:Long?=null,val latency:Int?=null)
data class IncidentState(val event:String,val server:String,val title:String,val level:String,val time:String)
data class ServiceState(val name:String,val status:String,val cpu:Int,val memory:Long,val restarts:Int)
data class SystemServiceState(val name:String,val label:String,val state:String,val active:Boolean,val enabled:String,val controllable:Boolean)
data class MobileOverview(val rx:Long=0,val tx:Long=0,val iface:String="--",val maintenance:Boolean=false,val backup:String?=null,val webOk:Int=0,val webTotal:Int=0,val latency:Int=0,val sslDays:Int?=null,val rxBytes:Long=0,val txBytes:Long=0,val connections:Int=0)
data class MonitorState(val level:String="loading",val cpu:Int=0,val ram:Int=0,val disk:Int=0,val temp:String="--",val alerts:Int=0,val servers:List<ServerState> = emptyList(),val error:String?=null)

class MainActivity:FragmentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  MobileApi.init(applicationContext);AlertNotifier.channel(applicationContext);MonitorScheduler.start(applicationContext)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),40)
  setContent{MaterialTheme(colorScheme=darkColorScheme(background=Navy,surface=Panel,primary=Blue,error=Red)){Surface(Modifier.fillMaxSize(),color=Navy){JRServerApp()}}}
 }
}

@Composable fun JRServerApp(){
 val context=LocalContext.current
 var state by remember{mutableStateOf(MonitorState())};var overview by remember{mutableStateOf(MobileOverview())};var incidents by remember{mutableStateOf(emptyList<IncidentState>())};var services by remember{mutableStateOf(emptyList<ServiceState>())};var systemServices by remember{mutableStateOf(emptyList<SystemServiceState>())};var detailServices by remember{mutableStateOf(emptyList<ServiceState>())};var detailSystemServices by remember{mutableStateOf(emptyList<SystemServiceState>())}
 var tab by remember{mutableIntStateOf(0)};var paired by remember{mutableStateOf(MobileApi.hasToken())};var selected by remember{mutableStateOf<ServerState?>(null)};var refreshing by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var maintenanceServer by remember{mutableStateOf<ServerState?>(null)}
 var rxHistory by remember{mutableStateOf(listOf<Long>())};var txHistory by remember{mutableStateOf(listOf<Long>())}
 fun refresh(){
  refreshing=true
  MobileApi.load { value ->
   (context as? Activity)?.runOnUiThread {
    state=value
    refreshing=false
    selected=selected?.let { old -> value.servers.find { it.id==old.id } ?: old }
   }
  }
  MobileApi.incidents { value -> (context as? Activity)?.runOnUiThread { incidents=value } }
  MobileApi.services { value -> (context as? Activity)?.runOnUiThread { services=value } }
  MobileApi.systemServices { value -> (context as? Activity)?.runOnUiThread { systemServices=value } }
  MobileApi.overview { value ->
   (context as? Activity)?.runOnUiThread {
    overview=value
    rxHistory=(rxHistory+value.rx).takeLast(60)
    txHistory=(txHistory+value.tx).takeLast(60)
   }
  }
 }
 if(!paired){PairScreen{paired=true};return}
 LaunchedEffect(paired){while(paired){refresh();delay(3000)}}
 if(settings){CommercialSettingsScreen(state.servers.firstOrNull(),overview,onBack={settings=false},onDisconnect={MobileApi.disconnect();paired=false;settings=false});return}
 if(maintenanceServer!=null){MaintenanceScreen(maintenanceServer!!,overview,onBack={maintenanceServer=null},onRefresh={refresh()});return}
 if(selected!=null){
  val current=selected!!
  LaunchedEffect(current.id){
   if(current.id=="local"){
    detailServices=services
    detailSystemServices=systemServices
   }else{
    detailServices=emptyList()
    detailSystemServices=emptyList()
    MobileApi.serverSystemServices(current.id){value->(context as? Activity)?.runOnUiThread{detailSystemServices=value}}
   }
  }
  LaunchedEffect(services,systemServices,current.id){if(current.id=="local"){detailServices=services;detailSystemServices=systemServices}}
  ServerDetail(current,detailServices,detailSystemServices,overview,incidents,rxHistory,txHistory,onBack={selected=null},onRefresh={
   if(current.id=="local")refresh() else MobileApi.serverSystemServices(current.id){value->(context as? Activity)?.runOnUiThread{detailSystemServices=value}}
  });return
 }
 Scaffold(containerColor=Navy,bottomBar={BottomNav(tab,state.alerts){tab=it}}){pad->Box(Modifier.padding(pad).fillMaxSize()){when(tab){1->IncidentList(incidents);2->ActionsScreen(state.servers.firstOrNull(),overview,onMaintenance={maintenanceServer=state.servers.firstOrNull()},onRefresh={refresh()});3->IncidentHistory(incidents);else->Dashboard(state,overview,refreshing,onRefresh={refresh()},onSettings={settings=true},onServer={selected=it},onAlerts={tab=1})}}}
}

@Composable
fun BottomNav(tab:Int, alerts:Int, onTab:(Int)->Unit) {
 val icons = listOf(Icons.Default.Home, Icons.Default.Notifications, Icons.Default.Bolt, Icons.Default.History)
 val labels = listOf("Inicio", "Alertas", "Acciones", "Historial")
 NavigationBar(containerColor = Color(0xFF07131F), tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
  labels.indices.forEach { i ->
   NavigationBarItem(
    selected = tab == i,
    onClick = { onTab(i) },
    icon = {
     BadgedBox(badge = { if (i == 1 && alerts > 0) Badge(containerColor = Red) { Text(alerts.toString()) } }) {
      Icon(icons[i], labels[i], modifier = Modifier.size(24.dp))
     }
    },
    label = { Text(labels[i], fontSize = 10.sp) },
    colors = NavigationBarItemDefaults.colors(
     selectedIconColor = Cyan, selectedTextColor = Cyan,
     unselectedIconColor = Muted, unselectedTextColor = Muted,
     indicatorColor = Color(0xFF0A2B47)
    )
   )
  }
 }
}

@Composable fun Header(title:String,subtitle:String,action:String?=null,onAction:(()->Unit)?=null){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(36.dp).background(Blue,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){Text("JR",fontWeight=FontWeight.Bold,color=Color.White)};Spacer(Modifier.width(10.dp));Column{Text(title,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=12.sp)}}
  if(action!=null)TextButton(onClick={onAction?.invoke()}){Text(action,color=Cyan)}
 }
}

@Composable
fun Dashboard(
 state: MonitorState,
 overview: MobileOverview,
 refreshing: Boolean,
 onRefresh: () -> Unit,
 onSettings: () -> Unit,
 onServer: (ServerState) -> Unit,
 onAlerts: () -> Unit
) {
 LazyColumn(
  Modifier.fillMaxSize().padding(horizontal = 18.dp),
  contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  item {
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("JR", fontSize = 31.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.width(9.dp))
    Text("Server Mobile", fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.weight(1f))
    BadgedBox(badge = { if (state.alerts > 0) Badge(containerColor = Red) { Text(state.alerts.toString()) } }) {
     IconButton(onClick = onAlerts) { Icon(Icons.Default.Notifications, "Alertas", tint = Color.White) }
    }
    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configuración", tint = Cyan) }
   }
  }
  item {
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Mis servidores", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    FilledIconButton(onClick = onRefresh, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Blue)) {
     Icon(Icons.Default.Add, "Agregar", tint = Color.White)
    }
   }
  }
  if (state.servers.isEmpty()) {
   item {
    Card(
     colors = CardDefaults.cardColors(containerColor = Panel),
     shape = RoundedCornerShape(18.dp),
     modifier = Modifier.fillMaxWidth()
    ) { Text("Esperando servidores…", Modifier.padding(22.dp), color = Muted) }
   }
  }
  items(state.servers) { s -> ApprovedServerCard(s, if (s == state.servers.firstOrNull()) overview.latency.takeIf { it > 0 } else null) { onServer(s) } }
  item {
   Text("Estado general", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
  }
  item {
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    MiniMetric("WEBS", overview.webOk.toString() + "/" + overview.webTotal, if (overview.webOk == overview.webTotal && overview.webTotal > 0) Green else Orange, Modifier.weight(1f))
    MiniMetric("LATENCIA", overview.latency.toString() + " ms", Blue, Modifier.weight(1f))
    MiniMetric("SSL", overview.sslDays?.let { it.toString() + " días" } ?: "--", if ((overview.sslDays ?: 99) < 15) Orange else Green, Modifier.weight(1f))
   }
  }
 }
}

@Composable
fun ApprovedServerCard(s: ServerState, fallbackLatency:Int?=null, onClick: () -> Unit) {
 val accent = if (s.online) Green else Red
 Card(
  Modifier.fillMaxWidth().clickable { onClick() },
  shape = RoundedCornerShape(18.dp),
  colors = CardDefaults.cardColors(containerColor = Color(0xFF06243B)),
  border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(accent.copy(alpha = .75f)))
 ) {
  Column(Modifier.padding(16.dp)) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(46.dp).background(Blue.copy(alpha = .18f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
     Icon(Icons.Default.Dns, "Servidor", tint = Blue, modifier = Modifier.size(27.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
     Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
     Text(s.host, color = Muted, fontSize = 11.sp)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = .17f)) {
     Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(7.dp).background(accent, CircleShape))
      Spacer(Modifier.width(6.dp))
      Text(if (s.online) "ONLINE" else "OFFLINE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
     }
    }
   }
   Spacer(Modifier.height(15.dp))
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
     CompactServerMetric("CPU", s.cpu, Green)
     CompactServerMetric("RAM", s.ram, Blue)
     CompactServerMetric("DISCO", s.disk, Orange)
    }
    LiveResourceBars(s, Modifier.weight(1.35f).height(92.dp))
    Icon(Icons.Default.ChevronRight, "Abrir", tint = Muted, modifier = Modifier.align(Alignment.CenterVertically))
   }
   Spacer(Modifier.height(9.dp))
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(s.uptime?.let { "Uptime " + formatUptime(it) } ?: "Esperando uptime", color = Muted, fontSize = 10.sp)
    Text((s.latency ?: fallbackLatency)?.let { it.toString() + " ms" } ?: "-- ms", color = if (((s.latency ?: fallbackLatency) ?: 0) < 100) Green else Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
   }
  }
 }
}

@Composable
fun CompactServerMetric(label: String, value: Int?, color: Color) {
 Row(verticalAlignment = Alignment.CenterVertically) {
  Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
  Spacer(Modifier.width(6.dp))
  Text(label, color = Muted, fontSize = 9.sp, modifier = Modifier.width(36.dp))
  Text(value?.let { it.toString() + "%" } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold)
 }
}

@Composable fun LiveResourceBars(s:ServerState,modifier:Modifier=Modifier){Canvas(modifier){val values=listOf((s.cpu?:0).toFloat(),(s.ram?:0).toFloat(),(s.disk?:0).toFloat());val colors=listOf(Green,Cyan,Orange);val gap=size.height/3f;values.forEachIndexed{i,value->val y=gap*i+gap/2f;drawLine(Color(0xFF164968),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),9f,cap=StrokeCap.Round);drawLine(colors[i],androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width*(value.coerceIn(0f,100f)/100f),y),9f,cap=StrokeCap.Round)}}}

@Composable fun ActionsScreen(server:ServerState?,overview:MobileOverview,onMaintenance:()->Unit,onRefresh:()->Unit){
 val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};var msg by remember{mutableStateOf<String?>(null)}
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ArrowBack,"Volver",tint=Color.White);Spacer(Modifier.width(18.dp));Text("Acciones rápidas",fontSize=26.sp,fontWeight=FontWeight.Bold)}}
  item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(Panel2,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Dns,"Servidor",tint=Color.White)};Spacer(Modifier.width(12.dp));Column{Text(server?.name?:"JR Server",fontWeight=FontWeight.Bold,fontSize=16.sp);Text(server?.host?:"--",color=Muted,fontSize=12.sp)}}}
  item{ActionGridRow(ActionSpec(Icons.Default.PowerSettingsNew,"Reiniciar servidor",Red,"reboot"),ActionSpec(Icons.Default.Build,"Modo mantenimiento",Orange,"open-maintenance")){if(it=="open-maintenance")onMaintenance() else ask=it}}
  item{ActionGridRow(ActionSpec(Icons.Default.CleaningServices,"Limpiar caché",Purple,"nginx-reload"),ActionSpec(Icons.Default.Refresh,"Reiniciar servicios",Blue,"nginx-reload")){ask=it}}
  item{ActionGridRow(ActionSpec(Icons.Default.CloudUpload,"Deploy Now",Green,"deploy-prod"),ActionSpec(Icons.Default.Storage,"Ejecutar backup",Color(0xFFFFC33B),"backup")){ask=it}}
  item{Card(Modifier.fillMaxWidth().clickable{MobileApi.quickLogs{m->(context as Activity).runOnUiThread{msg=m}}},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF351274)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(Purple))){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Description,"Logs",tint=Purple,modifier=Modifier.size(36.dp));Spacer(Modifier.width(16.dp));Text("Ver logs del incidente",fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.weight(1f));Icon(Icons.Default.ChevronRight,"Abrir")}}}
 }
 if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text("Confirmar acción")},text={Text("La acción se ejecutará en el servidor.")},confirmButton={TextButton(onClick={val x=ask!!;ask=null;authenticate(context as FragmentActivity){if(x=="deploy-prod")MobileApi.deploy("prod"){_,_->onRefresh()} else MobileApi.quickAction(x){_,_->onRefresh()}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})
}
data class ActionSpec(val icon:androidx.compose.ui.graphics.vector.ImageVector,val label:String,val color:Color,val action:String)
@Composable fun ActionGridRow(a:ActionSpec,b:ActionSpec,onAction:(String)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){MockAction(a,Modifier.weight(1f),onAction);MockAction(b,Modifier.weight(1f),onAction)}}
@Composable fun MockAction(x:ActionSpec,modifier:Modifier,onAction:(String)->Unit){Card(modifier.height(150.dp).clickable{onAction(x.action)},shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=x.color.copy(alpha=.38f)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(x.color))){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(x.icon,x.label,tint=x.color,modifier=Modifier.size(46.dp));Spacer(Modifier.height(18.dp));Text(x.label,fontWeight=FontWeight.Bold,fontSize=15.sp)}}}

@Composable fun IncidentHistory(data:List<IncidentState>){
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Historial",fontSize=25.sp,fontWeight=FontWeight.Bold)}
  if(data.isEmpty()) item{Text("No hay incidentes registrados",color=Muted)}
  items(data){x->Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(x.event=="resolved")Icons.Default.CheckCircle else Icons.Default.Warning,"Estado",tint=if(x.event=="resolved")Green else Orange);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.Bold);Text(x.server+" · "+humanTime(x.time),color=Muted,fontSize=11.sp)}}}}
 }
}

@Composable fun QuickActions(overview:MobileOverview,onRefresh:()->Unit){
 val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};var msg by remember{mutableStateOf<String?>(null)}
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ActionTile(Icons.Default.Build,if(overview.maintenance)"Salir mantenimiento" else "Mantenimiento",Orange,Modifier.weight(1f)){ask=if(overview.maintenance)"maintenance-off" else "maintenance-on"};ActionTile(Icons.Default.Backup,"Backup",Green,Modifier.weight(1f)){ask="backup"}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ActionTile(Icons.Default.Refresh,"Recargar Nginx",Cyan,Modifier.weight(1f)){ask="nginx-reload"};ActionTile(Icons.Default.RestartAlt,"Reiniciar servidor",Red,Modifier.weight(1f)){ask="reboot"}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ActionTile(Icons.Default.CloudDownload,"Deploy web",Purple,Modifier.weight(1f)){ask="deploy-prod"};ActionTile(Icons.Default.Description,"Logs rápidos",Blue,Modifier.weight(1f)){MobileApi.quickLogs{m->(context as? Activity)?.runOnUiThread{msg=m}}}}
  msg?.let{Text(it,color=Muted,fontSize=11.sp,modifier=Modifier.padding(4.dp))}
 }
 if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text("Confirmar acción")},text={Text(if(ask=="reboot")"El servidor quedará temporalmente fuera de línea." else "La acción se ejecutará en el servidor.")},confirmButton={TextButton(onClick={val a=ask!!;ask=null;authenticate(context as FragmentActivity){if(a=="deploy-prod")MobileApi.deploy("prod"){ok,m->context.runOnUiThread{msg=m;if(ok)onRefresh()}} else MobileApi.quickAction(a){ok,m->context.runOnUiThread{msg=m;if(ok)onRefresh()}}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})
}
@Composable fun ActionTile(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,color:Color,modifier:Modifier,onClick:()->Unit){Card(modifier.clickable{onClick()},colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(vertical=15.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,label,tint=color,modifier=Modifier.size(26.dp));Spacer(Modifier.height(7.dp));Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold)}}}
fun authenticate(activity:FragmentActivity,onSuccess:()->Unit){val executor=ContextCompat.getMainExecutor(activity);val prompt=BiometricPrompt(activity,executor,object:BiometricPrompt.AuthenticationCallback(){override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){onSuccess()}});val info=BiometricPrompt.PromptInfo.Builder().setTitle("Confirmar acción crítica").setSubtitle("JR Server Mobile").setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();prompt.authenticate(info)}
fun formatBytesTotal(value:Long):String=when{value>=1_000_000_000L->"%.1f GB".format(value/1e9);value>=1_000_000L->"%.1f MB".format(value/1e6);value>=1_000L->"%.1f KB".format(value/1e3);else->"$value B"}
fun formatRate(v:Long):String{val b=v.toDouble();return when{b>=125000000->"%.1f Gbps".format(b*8/1e9);b>=125000->"%.1f Mbps".format(b*8/1e6);b>=125->"%.0f Kbps".format(b*8/1e3);else->v.toString()+" B/s"}}

@Composable fun StatusHero(s:MonitorState){
 val ok=s.level=="ok";val warn=s.level=="warning";val c=if(ok)Green else if(warn)Orange else Red
 Card(colors=CardDefaults.cardColors(containerColor=Panel2),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(58.dp).background(c.copy(alpha=.16f),CircleShape),contentAlignment=Alignment.Center){Text(if(ok)"✓" else "!",fontSize=32.sp,color=c,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(15.dp));Column{Text(if(ok)"SISTEMA ONLINE" else if(warn)"ADVERTENCIA" else "FALLA DETECTADA",color=c,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(if(ok)"Servicios supervisados funcionando" else "Revisá las alertas activas",color=Muted);s.error?.let{Text(it,color=Red,fontSize=12.sp)}}}}
}

@Composable fun Gauge(label:String,value:Int,color:Color,modifier:Modifier=Modifier,display:String="${value}%"){
 Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(contentAlignment=Alignment.Center){CircularProgressIndicator(progress={value.coerceIn(0,100)/100f},modifier=Modifier.size(78.dp),color=color,trackColor=Color(0xFF1C3045),strokeWidth=8.dp);Text(display,fontWeight=FontWeight.Bold,fontSize=18.sp)};Spacer(Modifier.height(8.dp));Text(label,fontWeight=FontWeight.Bold)}}
}

@Composable fun SectionTitle(t:String){Text(t,fontWeight=FontWeight.Bold,fontSize=14.sp,color=Color(0xFFDCE8F5))}

@Composable fun ServerList(servers:List<ServerState>,open:(ServerState)->Unit){
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("JR SERVER","Servidores")};item{Card(colors=CardDefaults.cardColors(containerColor=Panel2),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(14.dp).background(if(servers.all{it.online})Green else Orange,CircleShape));Spacer(Modifier.width(12.dp));Column{Text("SERVIDORES",fontWeight=FontWeight.Bold);Text("${servers.count{it.online}} en línea / ${servers.count{!it.online}} fuera de servicio",color=Muted)}}}};items(servers){ApprovedServerCard(it){open(it)}}}
}

@Composable fun ServerDetail(server:ServerState,services:List<ServiceState>,systemServices:List<SystemServiceState>,overview:MobileOverview,incidents:List<IncidentState>,rxHistory:List<Long>,txHistory:List<Long>,onBack:()->Unit,onRefresh:()->Unit){
 var page by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize()){
  ServerTop(server,onBack)
  ServerTabs(page){page=it}
  Box(Modifier.weight(1f)){when(page){0->SummaryTab(server,overview,rxHistory,txHistory);1->ServicesTab(services,systemServices,onRefresh);2->NetworkTab(overview,rxHistory,txHistory);else->LogsTab(incidents)}}
 }
}
@Composable fun ServerTop(server:ServerState,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(16.dp,14.dp,16.dp,8.dp),verticalAlignment=Alignment.CenterVertically){FilledIconButton(onClick=onBack,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Panel2)){Icon(Icons.Default.ArrowBack,"Volver")};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(server.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(server.host,color=Muted,fontSize=11.sp)};Column(horizontalAlignment=Alignment.End){Surface(shape=RoundedCornerShape(14.dp),color=Green.copy(alpha=.34f)){Text(if(server.online)"ONLINE" else "OFFLINE",Modifier.padding(horizontal=12.dp,vertical=5.dp),color=if(server.online)Green else Red,fontWeight=FontWeight.Bold,fontSize=11.sp)};Text("Hace 2 min",color=Muted,fontSize=10.sp)};Icon(Icons.Default.MoreVert,"Opciones",tint=Muted)}
}
@Composable fun ServerTabs(page:Int,set:(Int)->Unit){val names=listOf("Resumen","Servicios","Red","Logs");Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).background(Color(0xFF071421),RoundedCornerShape(14.dp))){names.forEachIndexed{i,n->Box(Modifier.weight(1f).clickable{set(i)}.then(if(page==i)Modifier.background(Color(0xFF063A76),RoundedCornerShape(12.dp)) else Modifier).padding(vertical=14.dp),contentAlignment=Alignment.Center){Text(n,color=if(page==i)Cyan else Color(0xFFC4D0E2),fontWeight=if(page==i)FontWeight.Bold else FontWeight.Normal)}}}}
@Composable fun SummaryTab(s:ServerState,o:MobileOverview,rxHistory:List<Long>,txHistory:List<Long>){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricBar("CPU",s.cpu?:0,Green,Modifier.weight(1f));MetricBar("RAM",s.ram?:0,Blue,Modifier.weight(1f));MetricBar("DISCO",s.disk?:0,Orange,Modifier.weight(1f))}};item{NetworkChart(o,rxHistory,txHistory)};item{Text("Estado general",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{StatusRows(o)}}}
@Composable fun MetricBar(label:String,value:Int,color:Color,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(13.dp),colors=CardDefaults.cardColors(containerColor=Panel),border=CardDefaults.outlinedCardBorder()){Column(Modifier.padding(14.dp)){Text(label,color=Muted,fontSize=12.sp);Text(value.toString()+"%",fontSize=23.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth().height(8.dp),color=color,trackColor=Panel2,strokeCap=StrokeCap.Round)}}}
@Composable fun NetworkChart(o:MobileOverview,rxHistory:List<Long>,txHistory:List<Long>){val peak=max(1L,(rxHistory+txHistory+listOf(o.rx,o.tx)).maxOrNull()?:1L);val step=when{peak<=25_000_000L->25_000_000L;peak<=50_000_000L->50_000_000L;peak<=100_000_000L->100_000_000L;peak<=250_000_000L->250_000_000L;peak<=500_000_000L->500_000_000L;else->1_000_000_000L};val top=max(step,ceil(peak.toDouble()/step).toLong()*step);Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF062A45)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(Blue.copy(alpha=.90f)))){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth()){Text("Tráfico de red",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("↓ "+formatRate(o.rx),color=Cyan);Spacer(Modifier.width(14.dp));Text("↑ "+formatRate(o.tx),color=Red)};Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth()){Column(Modifier.height(120.dp),verticalArrangement=Arrangement.SpaceBetween){Text(formatAxis(top),color=Muted,fontSize=9.sp);Text(formatAxis(top/2),color=Muted,fontSize=9.sp);Text("0",color=Muted,fontSize=9.sp)};Spacer(Modifier.width(8.dp));Canvas(Modifier.weight(1f).height(120.dp)){listOf(0f,.5f,1f).forEach{fraction->val y=size.height*fraction;drawLine(Color(0xFF164968),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),1f)};fun drawSeries(values:List<Long>,color:Color){if(values.size<2)return;val p=Path();values.forEachIndexed{i,value->val x=size.width*i/(values.size-1);val y=size.height-(size.height*(value.toFloat()/top.toFloat()).coerceIn(0f,1f));if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};drawPath(p,color,style=Stroke(width=4f,cap=StrokeCap.Round))};drawSeries(rxHistory,Cyan);drawSeries(txHistory,Green)}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Hace 3 min",color=Muted,fontSize=10.sp);Text("Ahora",color=Muted,fontSize=10.sp)}}}}
fun formatAxis(value:Long):String=when{value>=1_000_000_000L->"%.1f GB/s".format(value/1e9);value>=1_000_000L->"${value/1_000_000} MB/s";value>=1_000L->"${value/1_000} KB/s";else->"$value B/s"}

@Composable fun StatusRows(o:MobileOverview){Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column{StatusLine(Icons.Default.Language,"HTTP / Web",if(o.webTotal>0)o.webOk.toString()+"/"+o.webTotal else "--",if(o.webOk==o.webTotal&&o.webTotal>0)Green else Orange,o.latency.toString()+" ms");StatusLine(Icons.Default.Lock,"SSL",o.sslDays?.let{"Válido"}?:"--",Green,o.sslDays?.let{it.toString()+" días"}?:"--");StatusLine(Icons.Default.Storage,"Base de datos","Monitoreada",Blue,"");StatusLine(Icons.Default.Dns,"PM2 (Panel)","Monitoreado",Blue,"");StatusLine(Icons.Default.Public,"Nginx","Monitoreado",Blue,"")}}}
@Composable fun StatusLine(icon:androidx.compose.ui.graphics.vector.ImageVector,name:String,state:String,color:Color,detail:String){Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,name,tint=color,modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(name,Modifier.weight(1f));Surface(shape=RoundedCornerShape(10.dp),color=color.copy(alpha=.15f)){Text(state,Modifier.padding(horizontal=9.dp,vertical=3.dp),color=color,fontSize=11.sp)};Spacer(Modifier.width(12.dp));Text(detail,color=Muted,fontSize=10.sp)}}
@Composable fun ServicesTab(services:List<ServiceState>,system:List<SystemServiceState>,onRefresh:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(top=14.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(system){CompactSystemService(it,onRefresh)};items(services){CompactPm2Service(it,onRefresh)};item{Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Refresh,"Actualizar");Spacer(Modifier.width(8.dp));Text("Actualizar servicios")}}}}
@Composable fun CompactSystemService(s:SystemServiceState,onDone:()->Unit){val context=LocalContext.current;var ask by remember{mutableStateOf(false)};val color=serviceColor(s.label);Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07263E)),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color.copy(alpha=.70f)))){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(color.copy(alpha=.34f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(serviceIcon(s.label),s.label,tint=color)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.label,fontWeight=FontWeight.Bold);Text(s.name,color=Muted,fontSize=10.sp)};Surface(shape=RoundedCornerShape(10.dp),color=(if(s.active)Green else Red).copy(alpha=.2f)){Text(if(s.active)"Online" else s.state,Modifier.padding(horizontal=9.dp,vertical=4.dp),color=if(s.active)Green else Red,fontSize=10.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(6.dp));if(s.controllable)IconButton(onClick={ask=true},modifier=Modifier.size(36.dp)){Icon(Icons.Default.Refresh,"Reiniciar",tint=Cyan)}else Icon(Icons.Default.Lock,"Protegido",tint=Orange,modifier=Modifier.size(20.dp))}};if(ask)AlertDialog(onDismissRequest={ask=false},title={Text("Reiniciar "+s.label)},text={Text("¿Confirmás reiniciar este servicio?")},confirmButton={TextButton(onClick={ask=false;MobileApi.systemServiceAction(s.name,"restart"){ok,_->if(ok)(context as? Activity)?.runOnUiThread{onDone()}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=false}){Text("CANCELAR")}})}
@Composable fun CompactPm2Service(s:ServiceState,onDone:()->Unit){val context=LocalContext.current;var ask by remember{mutableStateOf(false)};val online=s.status=="online";Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07263E)),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(Purple.copy(alpha=.70f)))){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Purple.copy(alpha=.34f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Memory,s.name,tint=Purple)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold);Text("PM2 · CPU "+s.cpu+"% · "+formatBytes(s.memory),color=Muted,fontSize=10.sp)};Surface(shape=RoundedCornerShape(10.dp),color=(if(online)Green else Red).copy(alpha=.2f)){Text(if(online)"Online" else s.status,Modifier.padding(horizontal=9.dp,vertical=4.dp),color=if(online)Green else Red,fontSize=10.sp,fontWeight=FontWeight.Bold)};IconButton(onClick={ask=true},modifier=Modifier.size(36.dp)){Icon(Icons.Default.Refresh,"Reiniciar",tint=Cyan)}}};if(ask)AlertDialog(onDismissRequest={ask=false},title={Text("Reiniciar "+s.name)},text={Text("¿Confirmás reiniciar este proceso PM2?")},confirmButton={TextButton(onClick={ask=false;MobileApi.serviceAction(s.name,"restart"){ok,_->if(ok)(context as? Activity)?.runOnUiThread{onDone()}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=false}){Text("CANCELAR")}})}
fun serviceColor(name:String)=when{name.contains("nginx",true)->Green;name.contains("mysql",true)->Blue;name.contains("fail",true)->Color(0xFFFFD54F);name.contains("cloud",true)->Orange;name.contains("cert",true)->Cyan;else->Purple}
fun serviceIcon(name:String)=when{name.contains("nginx",true)->Icons.Default.Public;name.contains("mysql",true)->Icons.Default.Storage;name.contains("fail",true)->Icons.Default.Security;name.contains("cloud",true)->Icons.Default.Cloud;else->Icons.Default.Settings}

@Composable
fun NetworkTab(o: MobileOverview, rxHistory:List<Long>, txHistory:List<Long>) {
 LazyColumn(
  Modifier.fillMaxSize().padding(16.dp),
  verticalArrangement = Arrangement.spacedBy(12.dp)
 ) {
  item { NetworkChart(o,rxHistory,txHistory) }
  item {
   Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
     Text("Uso de ancho de banda", fontWeight = FontWeight.Bold, fontSize = 18.sp)
     Spacer(Modifier.height(14.dp))
     Row {
      Column(Modifier.weight(1f)) {
       Text("↓ Descarga", color = Green)
       Text(formatBytesTotal(o.rxBytes), fontSize = 25.sp, fontWeight = FontWeight.Bold)
      }
      Column(Modifier.weight(1f)) {
       Text("↑ Subida", color = Red)
       Text(formatBytesTotal(o.txBytes), fontSize = 25.sp, fontWeight = FontWeight.Bold)
      }
     }
    }
   }
  }
  item {
   Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
     Text("Conexiones activas", fontWeight = FontWeight.Bold)
     Text(o.connections.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Green)
    }
   }
  }
  item { Text("Latencia y respuesta", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
  item {
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    MiniMetric("Ping", o.latency.toString() + " ms", Green, Modifier.weight(1f))
    MiniMetric("Jitter", "--", Blue, Modifier.weight(1f))
    MiniMetric("Pérdida", "--", Orange, Modifier.weight(1f))
   }
  }
  item { Text("Interfaces de red", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
  item {
   StatusLine(
    Icons.Default.Lan,
    o.iface,
    "Activa",
    Green,
    formatRate(o.rx) + " ↓  " + formatRate(o.tx) + " ↑"
   )
  }
 }
}
@Composable fun LogsTab(data:List<IncidentState>){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Logs del incidente",fontSize=22.sp,fontWeight=FontWeight.Bold)};if(data.isEmpty())item{Text("No hay incidentes registrados",color=Muted)};items(data.take(20)){x->Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(x.title,color=if(x.event=="resolved")Green else Red,fontWeight=FontWeight.Bold);Text(x.server+" · "+x.time,color=Muted,fontSize=11.sp)}}}}}

@Composable fun MiniMetric(label:String,value:String,color:Color,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(16.dp)){Text(label,color=Muted,fontSize=11.sp);Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold,color=color)}}}

@Composable fun SystemServiceCard(s:SystemServiceState,onDone:()->Unit){
 val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var msg by remember{mutableStateOf<String?>(null)}
 Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(15.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(s.label,fontWeight=FontWeight.Bold);Text(s.name+" · "+s.enabled,color=Muted,fontSize=11.sp)};Text(if(s.active)"● ACTIVO" else "● "+s.state.uppercase(),color=if(s.active)Green else Red,fontSize=11.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(12.dp));if(s.controllable){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(!s.active)SmallAction("Iniciar",Green){ask="start"};SmallAction("Reiniciar",Blue){ask="restart"};SmallAction("Detener",Red){ask="stop"}}}else Text("PROTEGIDO · Control remoto deshabilitado",color=Orange,fontSize=11.sp,fontWeight=FontWeight.Bold);msg?.let{Text(it,color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))}}}
 if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text(when(ask){"stop"->"Detener servicio";"start"->"Iniciar servicio";else->"Reiniciar servicio"})},text={Text("¿Confirmás "+askText(ask!!)+" "+s.label+"?")},confirmButton={TextButton(enabled=!busy,onClick={val action=ask!!;busy=true;MobileApi.systemServiceAction(s.name,action){ok,message->(context as? Activity)?.runOnUiThread{busy=false;ask=null;msg=message;if(ok)onDone()}}}){Text(if(busy)"…" else "CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})
}

@Composable fun ServiceCard(s:ServiceState,onDone:()->Unit){
 val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var msg by remember{mutableStateOf<String?>(null)};val online=s.status=="online"
 Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(15.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(s.name,fontWeight=FontWeight.Bold);Text("CPU ${s.cpu}% · RAM ${formatBytes(s.memory)} · Reinicios ${s.restarts}",color=Muted,fontSize=11.sp)};Text(if(online)"● EN EJECUCIÓN" else "● ${s.status.uppercase()}",color=if(online)Green else Red,fontSize=11.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(!online)SmallAction("▶ Iniciar",Green){ask="start"};SmallAction("↻ Reiniciar",Blue){ask="restart"};if(s.name!="jr-server-panel")SmallAction("■ Detener",Red){ask="stop"}};msg?.let{Text(it,color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))}}}
 if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text(when(ask){"stop"->"Detener servicio";"start"->"Iniciar servicio";else->"Reiniciar servicio"})},text={Text("¿Confirmás ${askText(ask!!)} ${s.name}?")},confirmButton={TextButton(enabled=!busy,onClick={val action=ask!!;busy=true;MobileApi.serviceAction(s.name,action){ok,message->(context as? Activity)?.runOnUiThread{busy=false;ask=null;msg=message;if(ok)onDone()}}}){Text(if(busy)"…" else "CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})
}

@Composable fun SmallAction(text:String,color:Color,onClick:()->Unit){OutlinedButton(onClick=onClick,contentPadding=PaddingValues(horizontal=10.dp,vertical=5.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=color)){Text(text,fontSize=11.sp)}}
fun askText(a:String)=when(a){"start"->"iniciar";"stop"->"detener";else->"reiniciar"}
fun formatBytes(v:Long):String=if(v<=0)"0 MB" else "${v/1024/1024} MB"
fun formatUptime(v:Long):String{val d=v/86400;val h=(v%86400)/3600;return if(d>0)"${d} d, ${h} h" else "${h} h"}

@Composable fun IncidentList(data:List<IncidentState>){
 var filter by remember{mutableIntStateOf(0)}
 val shown=data.filter{filter==0||(filter==1&&it.level.contains("crit",true))||(filter==2&&!it.level.contains("crit",true))}
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){Text("JR",fontSize=31.sp,fontWeight=FontWeight.Black);Spacer(Modifier.width(9.dp));Text("Server Mobile",fontSize=15.sp);Spacer(Modifier.weight(1f));BadgedBox(badge={if(data.isNotEmpty())Badge(containerColor=Red){Text(data.size.toString())}}){Icon(Icons.Default.Notifications,"Alertas")};Spacer(Modifier.width(16.dp));Icon(Icons.Default.Settings,"Ajustes")}}
  item{Text("Alertas",fontSize=28.sp,fontWeight=FontWeight.Bold)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=filter==0,onClick={filter=0},label={Text("Todas")});FilterChip(selected=filter==1,onClick={filter=1},label={Text("🔴 Críticas")});FilterChip(selected=filter==2,onClick={filter=2},label={Text("🟠 Advertencias")})}}
  if(shown.isEmpty())item{Text("No hay alertas en este filtro",color=Muted)}
  items(shown){x->AlertCard(x)}
 }
}
@Composable fun AlertCard(x:IncidentState){val critical=x.level.contains("crit",true)||x.title.contains("502");val info=x.event=="resolved"||x.title.contains("backup",true)||x.title.contains("reinici",true);val color=if(info)Blue else if(critical)Red else Orange;Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.30f)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color))){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(color,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(if(info)Icons.Default.Info else Icons.Default.Warning,"Alerta",tint=Color.White)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(x.server,color=Muted,fontSize=12.sp);Text(humanTime(x.time),color=Muted,fontSize=11.sp)};Icon(Icons.Default.ChevronRight,"Abrir",tint=Muted)}}}

@Composable fun SettingsScreen(onDisconnect:()->Unit){
 var confirm by remember{mutableStateOf(false)}
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("JR SERVER","Ajustes")};item{SectionTitle("CONFIGURACIÓN DE LA APP")};item{SettingCard("▣","URL del panel",MobileApi.panelUrl())};item{SettingCard("↻","Actualización","Segundo plano cada 15 minutos")};item{SectionTitle("SEGURIDAD")};item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth().clickable{confirm=true}){Text("⇥  Desvincular dispositivo",Modifier.padding(18.dp),color=Red,fontWeight=FontWeight.Bold)}};item{SectionTitle("INFORMACIÓN")};item{SettingCard("ⓘ","Versión de la app","v1.0.0")}}
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Desvincular teléfono")},text={Text("La app dejará de consultar este JR Server Panel.")},confirmButton={TextButton(onClick=onDisconnect){Text("DESVINCULAR")}},dismissButton={TextButton(onClick={confirm=false}){Text("CANCELAR")}})
}
@Composable fun SettingCard(icon:String,title:String,sub:String){Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,color=Blue,fontSize=22.sp);Spacer(Modifier.width(12.dp));Column{Text(title,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=12.sp)}}}}

@Composable fun MaintenanceVisual(s:ServerState){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Mantenimiento",fontSize=26.sp,fontWeight=FontWeight.Bold)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricBar("CPU",s.cpu?:0,Green,Modifier.weight(1f));MetricBar("RAM",s.ram?:0,Blue,Modifier.weight(1f));MetricBar("DISCO",s.disk?:0,Orange,Modifier.weight(1f))}};item{Text("Temperatura del sistema",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniMetric("CPU",s.temp?.plus("°C")?:"--",Green,Modifier.weight(1f));MiniMetric("Placa base","--",Blue,Modifier.weight(1f));MiniMetric("Fuente","--",Orange,Modifier.weight(1f))}};item{Text("Ventiladores",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Text("Sin datos de sensores disponibles",color=Muted)}}}

fun humanTime(raw:String):String=try{val instant=Instant.parse(raw);val mins=(System.currentTimeMillis()-instant.toEpochMilli())/60000;when{mins<1->"Ahora";mins<60->"Hace "+mins+" min";mins<1440->"Hace "+(mins/60)+" h";else->DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(instant)}}catch(_:Exception){raw}

@Composable fun MaintenanceScreen(s:ServerState,o:MobileOverview,onBack:()->Unit,onRefresh:()->Unit){val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Volver")};Column(Modifier.weight(1f)){Text("Mantenimiento",fontSize=24.sp,fontWeight=FontWeight.Bold);Text(s.host,color=Muted,fontSize=11.sp)};Text(if(s.online)"ONLINE" else "OFFLINE",color=if(s.online)Green else Red,fontWeight=FontWeight.Bold)}};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricBar("CPU",s.cpu?:0,Green,Modifier.weight(1f));MetricBar("RAM",s.ram?:0,Cyan,Modifier.weight(1f));MetricBar("DISCO",s.disk?:0,Orange,Modifier.weight(1f))}};item{Text("Temperatura del sistema",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniMetric("CPU",s.temp?.plus("°C")?:"--",Green,Modifier.weight(1f));MiniMetric("Placa","--",Blue,Modifier.weight(1f));MiniMetric("Fuente","--",Orange,Modifier.weight(1f))}};item{Text("Ventiladores",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Text("Sensores RPM no disponibles en este equipo",Modifier.padding(14.dp),color=Muted)}};item{Text("Herramientas de mantenimiento",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{MaintenanceAction(Icons.Default.Build,if(o.maintenance)"Desactivar modo mantenimiento" else "Activar modo mantenimiento",Orange){ask=if(o.maintenance)"maintenance-off" else "maintenance-on"}};item{MaintenanceAction(Icons.Default.Refresh,"Validar y recargar Nginx",Cyan){ask="nginx-reload"}};item{MaintenanceAction(Icons.Default.Backup,"Ejecutar backup",Green){ask="backup"}}};if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text("Confirmar mantenimiento")},text={Text("La acción se ejecutará en el servidor.")},confirmButton={TextButton(onClick={val action=ask!!;ask=null;authenticate(context as FragmentActivity){MobileApi.quickAction(action){ok,_->if(ok)context.runOnUiThread{onRefresh()}}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})}
@Composable fun MaintenanceAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,color:Color,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable{onClick()},colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.2f)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color.copy(alpha=.90f)))){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,title,tint=color);Spacer(Modifier.width(12.dp));Text(title,Modifier.weight(1f),fontWeight=FontWeight.Bold);Icon(Icons.Default.ChevronRight,"Abrir",tint=color)}}}

@Composable
fun CommercialSettingsScreen(server:ServerState?,overview:MobileOverview,onBack:()->Unit,onDisconnect:()->Unit){
 var section by remember{mutableIntStateOf(0)}
 var updates by remember{mutableStateOf(true)}
 var backups by remember{mutableStateOf(true)}
 var email by remember{mutableStateOf(true)}
 var whatsapp by remember{mutableStateOf(true)}
 var critical by remember{mutableStateOf(true)}
 var warnings by remember{mutableStateOf(true)}
 var biometric by remember{mutableStateOf(true)}
 var destructiveAuth by remember{mutableStateOf(true)}
 var darkMode by remember{mutableStateOf(true)}
 val tabs=listOf("General","Red","Notificaciones","Seguridad","Apariencia")
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Volver")};Column(Modifier.weight(1f)){Text("Configuración",fontSize=24.sp,fontWeight=FontWeight.Bold);Text(server?.host?:MobileApi.panelUrl(),color=Muted,fontSize=11.sp)}}}
  item{Row(Modifier.fillMaxWidth().background(Color(0xFF05233B),RoundedCornerShape(12.dp)),horizontalArrangement=Arrangement.SpaceEvenly){tabs.forEachIndexed{i,name->Box(Modifier.weight(1f).clickable{section=i}.then(if(section==i)Modifier.background(Blue.copy(alpha=.34f),RoundedCornerShape(10.dp)) else Modifier).padding(vertical=13.dp),contentAlignment=Alignment.Center){Text(name,color=if(section==i)Cyan else Muted,fontSize=9.sp,fontWeight=if(section==i)FontWeight.Bold else FontWeight.Normal)}}}}
  when(section){
   0->{
    item{SettingsInfo(Icons.Default.Dns,"Información del servidor",server?.name?:"JR Server",Blue)}
    item{SettingsToggle(Icons.Default.SystemUpdate,"Actualizaciones","Actualizar automáticamente",Purple,updates){updates=it}}
    item{SettingsToggle(Icons.Default.Backup,"Backups automáticos","Copia de seguridad programada",Green,backups){backups=it}}
   }
   1->{
    item{SettingsInfo(Icons.Default.Language,"Servidor / host",server?.host?:MobileApi.panelUrl(),Cyan)}
    item{SettingsInfo(Icons.Default.Wifi,"Estado de conexión",if(server?.online==true)"ONLINE" else "OFFLINE",if(server?.online==true)Green else Red)}
    item{SettingsInfo(Icons.Default.Speed,"Latencia",(server?.latency ?: overview.latency.takeIf{it>0})?.let{"$it ms"}?:"Sin datos",Blue)}
   }
   2->{
    item{SettingsToggle(Icons.Default.Notifications,"Notificaciones por email","Alertas importantes",Orange,email){email=it}}
    item{SettingsToggle(Icons.Default.Chat,"Notificaciones WhatsApp","Alertas operativas",Green,whatsapp){whatsapp=it}}
    item{SettingsToggle(Icons.Default.Error,"Alertas críticas","Incidentes de prioridad alta",Red,critical){critical=it}}
    item{SettingsToggle(Icons.Default.Warning,"Advertencias","Avisos preventivos",Orange,warnings){warnings=it}}
   }
   3->{
    item{SettingsToggle(Icons.Default.Fingerprint,"Biometría","Confirmar acceso con biometría",Cyan,biometric){biometric=it}}
    item{SettingsToggle(Icons.Default.Security,"Acciones críticas","Pedir autenticación antes de ejecutar",Red,destructiveAuth){destructiveAuth=it}}
    item{SettingsInfo(Icons.Default.Lock,"Conexión segura","HTTPS obligatorio",Green)}
   }
   else->{
    item{SettingsToggle(Icons.Default.DarkMode,"Modo oscuro","Interfaz oscura JR Server Mobile",Purple,darkMode){darkMode=it}}
    item{SettingsInfo(Icons.Default.Palette,"Tema","Neón JR Server",Cyan)}
    item{var selectedColor by remember{mutableIntStateOf(0)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf(Cyan,Green,Orange,Purple,Red).forEachIndexed{i,color->Box(Modifier.size(if(selectedColor==i)48.dp else 42.dp).background(color.copy(alpha=if(selectedColor==i).62f else .34f),CircleShape).border(if(selectedColor==i)4.dp else 2.dp,color,CircleShape).clickable{selectedColor=i},contentAlignment=Alignment.Center){if(selectedColor==i)Icon(Icons.Default.Check,"Seleccionado",tint=Color.White,modifier=Modifier.size(22.dp))}}}}
   }
  }
  item{Button(onClick={},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,"Guardar");Spacer(Modifier.width(8.dp));Text("Guardar cambios")}}
  if(section==3)item{OutlinedButton(onClick=onDisconnect,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Red)){Text("Desvincular dispositivo")}}
 }
}

@Composable fun SettingsInfo(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,value:String,color:Color){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07263E)),modifier=Modifier.fillMaxWidth(),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color.copy(alpha=.62f)))){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).background(color.copy(alpha=.34f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(icon,title,tint=color)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(value,color=Muted,fontSize=11.sp)}}}}

@Composable fun SettingsToggle(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,sub:String,color:Color,value:Boolean,onChange:(Boolean)->Unit){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07263E)),modifier=Modifier.fillMaxWidth(),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color.copy(alpha=.62f)))){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).background(color.copy(alpha=.34f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(icon,title,tint=color)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=10.sp)};Switch(checked=value,onCheckedChange=onChange)}}}

@Composable fun PairScreen(done:()->Unit){
 val context=LocalContext.current;var panel by remember{mutableStateOf(MobileApi.panelUrl())};var user by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Header("JR SERVER","Vincular este teléfono");Spacer(Modifier.height(24.dp));OutlinedTextField(panel,{panel=it},label={Text("URL del panel")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(user,{user=it},label={Text("Usuario")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(pass,{pass=it},label={Text("Contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text("Código 2FA")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(14.dp));Button(onClick={busy=true;error=null;MobileApi.pair(panel,user,pass,code){ok,msg->(context as? Activity)?.runOnUiThread{busy=false;if(ok){MonitorScheduler.start(MobileApi.appContext());done()}else error=msg}}},enabled=!busy&&panel.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank()&&code.length==6,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Vinculando…" else "VINCULAR")};error?.let{Text(it,color=Red,modifier=Modifier.padding(top=10.dp))}}
}

object MobileApi{
 private const val DEFAULT_URL="https://jrserver.jrelectricidad.com.ar";private val client=OkHttpClient();private lateinit var context:Context
 fun init(c:Context){context=c.applicationContext};fun appContext()=context;private fun prefs()=context.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE);fun panelUrl()=(prefs().getString("panel_url",DEFAULT_URL)?:DEFAULT_URL).trimEnd('/');private fun normalizeUrl(v:String):String{var x=v.trim().trimEnd('/');if(!x.startsWith("https://")&&!x.startsWith("http://"))x="https://"+x;require(x.startsWith("https://")){"JR Server Mobile requiere HTTPS"};return x};private fun token()=prefs().getString("token","")?:"";fun hasToken()=token().isNotBlank();private fun saveToken(v:String){prefs().edit().putString("token",v).apply()}
 fun disconnect(){prefs().edit().remove("token").remove("last_level").remove("active_incidents").apply();WorkManager.getInstance(context).cancelUniqueWork("jr-monitor")}
 private fun request(path:String,method:String="GET",body:String?=null,done:(Int,String)->Unit){val b=Request.Builder().url(panelUrl()+path).header("Authorization","Bearer "+token());if(method=="POST")b.post((body?:"{}").toRequestBody("application/json".toMediaType()));client.newCall(b.build()).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=done(0,"");override fun onResponse(c:Call,r:Response){r.use{done(it.code,it.body?.string()?:"")}}})}
 fun pair(panel:String,user:String,pass:String,code:String,done:(Boolean,String)->Unit){val json=JSONObject().put("username",user).put("password",pass).put("code",code).put("deviceName",Build.MANUFACTURER+" "+Build.MODEL).toString();val base=try{normalizeUrl(panel)}catch(e:Exception){done(false,e.message?:"URL inválida");return};val req=Request.Builder().url(base+"/api/mobile/v1/pair").post(json.toRequestBody("application/json".toMediaType())).build();client.newCall(req).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=done(false,"No se pudo conectar");override fun onResponse(c:Call,r:Response){r.use{try{val j=JSONObject(it.body?.string()?:"{}");if(it.isSuccessful&&j.optString("token").isNotBlank()){prefs().edit().putString("panel_url",base).apply();saveToken(j.getString("token"));done(true,"")}else done(false,"Credenciales o 2FA incorrectos")}catch(e:Exception){done(false,"Respuesta no válida")}}}})}
 fun statusJson(done:(JSONObject?)->Unit)=request("/api/mobile/v1/status"){code,raw->done(if(code in 200..299)runCatching{JSONObject(raw)}.getOrNull() else null)}
 fun overview(done:(MobileOverview)->Unit)=request("/api/mobile/v1/overview"){code,raw->if(code !in 200..299){done(MobileOverview());return@request};try{val j=JSONObject(raw);val n=j.getJSONObject("network");val b=j.optJSONObject("backup");val h=j.optJSONArray("health");var webOk=0;var latency=0;var latencyCount=0;if(h!=null)for(i in 0 until h.length()){val x=h.getJSONObject(i);if(x.optBoolean("ok"))webOk++;if(!x.isNull("latencyMs")){val ms=x.optInt("latencyMs",-1);if(ms>=0){latency+=ms;latencyCount++}}};if(latencyCount>0)latency/=latencyCount;val c=j.optJSONArray("certificates");var ssl:Int?=null;if(c!=null)for(i in 0 until c.length()){val d=c.getJSONObject(i).optInt("days",-1);if(d>=0&&(ssl==null||d<ssl!!))ssl=d};done(MobileOverview(n.optLong("rxPerSec"),n.optLong("txPerSec"),n.optString("interface","--"),j.optBoolean("maintenance"),b?.optJSONObject("last")?.optString("time"),webOk,h?.length()?:0,latency,ssl,n.optLong("rxBytes"),n.optLong("txBytes"),n.optInt("connections")))}catch(e:Exception){done(MobileOverview())}}
 fun quickAction(action:String,done:(Boolean,String)->Unit)=request("/api/mobile/v1/actions/"+action,"POST","{}"){code,raw->val ok=code in 200..299;done(ok,runCatching{JSONObject(raw).optString("message",if(ok)"Acción completada" else "Error")}.getOrDefault("Error"))}
 fun deploy(project:String,done:(Boolean,String)->Unit)=request("/api/mobile/v1/deploy/"+project,"POST","{}"){code,raw->val ok=code in 200..299;done(ok,runCatching{JSONObject(raw).optString("message",if(ok)"Deploy completado" else "Deploy falló")}.getOrDefault("Deploy falló"))}
 fun quickLogs(done:(String)->Unit)=request("/api/mobile/v1/quick-logs"){code,raw->if(code !in 200..299){done("No se pudieron cargar los logs");return@request};done(runCatching{val a=JSONObject(raw).getJSONArray("lines");buildString{for(i in maxOf(0,a.length()-10) until a.length())append(a.optString(i)).append("\n")}.trim()}.getOrDefault("Sin logs"))}
 fun incidents(done:(List<IncidentState>)->Unit)=request("/api/mobile/v1/incidents?limit=100"){code,raw->if(code !in 200..299){done(emptyList());return@request};try{val a=JSONObject(raw).optJSONArray("history");val out=buildList{if(a!=null)for(i in a.length()-1 downTo 0){val x=a.getJSONObject(i);add(IncidentState(x.optString("event"),x.optString("server"),x.optString("title"),x.optString("level"),x.optString("time")))}};done(out)}catch(e:Exception){done(emptyList())}}
 fun services(done:(List<ServiceState>)->Unit)=request("/api/mobile/v1/services"){code,raw->if(code !in 200..299){done(emptyList());return@request};try{val a=JSONObject(raw).getJSONArray("services");val out=buildList{for(i in 0 until a.length()){val x=a.getJSONObject(i);add(ServiceState(x.optString("name"),x.optString("status"),x.optInt("cpu"),x.optLong("memory"),x.optInt("restarts")))}};done(out)}catch(e:Exception){done(emptyList())}}
 fun systemServices(done:(List<SystemServiceState>)->Unit)=request("/api/mobile/v1/system-services"){code,raw->if(code !in 200..299){done(emptyList());return@request};try{val a=JSONObject(raw).getJSONArray("services");val out=buildList{for(i in 0 until a.length()){val x=a.getJSONObject(i);add(SystemServiceState(x.optString("name"),x.optString("label"),x.optString("state"),x.optBoolean("active"),x.optString("enabled"),x.optBoolean("controllable")))}};done(out)}catch(e:Exception){done(emptyList())}}
 fun serverSystemServices(id:String,done:(List<SystemServiceState>)->Unit)=request("/api/mobile/v1/servers/${java.net.URLEncoder.encode(id,"UTF-8")}/system-services"){code,raw->if(code !in 200..299){done(emptyList());return@request};try{val a=JSONObject(raw).getJSONArray("services");val out=buildList{for(i in 0 until a.length()){val x=a.getJSONObject(i);add(SystemServiceState(x.optString("name"),x.optString("label"),x.optString("state"),x.optBoolean("active"),x.optString("enabled"),false))}};done(out)}catch(e:Exception){done(emptyList())}}
 fun systemServiceAction(name:String,action:String,done:(Boolean,String)->Unit)=request("/api/mobile/v1/system-services/${java.net.URLEncoder.encode(name,"UTF-8")}/$action","POST","{}"){code,raw->val ok=code in 200..299;val message=if(ok)"Acción ejecutada correctamente" else runCatching{JSONObject(raw).optString("error")}.getOrDefault("Error al ejecutar");done(ok,message)}
 fun serviceAction(name:String,action:String,done:(Boolean,String)->Unit)=request("/api/mobile/v1/services/${java.net.URLEncoder.encode(name,"UTF-8")}/$action","POST","{}"){code,raw->val ok=code in 200..299;val message=if(ok)"Acción ejecutada correctamente" else runCatching{JSONObject(raw).optString("error")}.getOrDefault("Error al ejecutar");done(ok,message)}
 fun load(done:(MonitorState)->Unit)=statusJson{j->if(j==null){done(MonitorState(level="critical",error="No se pudo consultar el panel"));return@statusJson};try{val summary=j.getJSONObject("summary");val local=j.getJSONObject("local");val arr=j.getJSONArray("servers");val servers=buildList{for(i in 0 until arr.length()){val x=arr.getJSONObject(i);add(ServerState(x.optString("id"),x.optString("name"),x.optBoolean("online"),x.optString("level"),x.optString("host"),if(x.has("cpuPercent"))x.optInt("cpuPercent") else null,if(x.has("memoryPercent"))x.optInt("memoryPercent") else null,if(x.has("diskPercent"))x.optInt("diskPercent") else null,if(x.has("temperature")&&!x.isNull("temperature"))x.optDouble("temperature").toString() else null,if(x.has("uptime"))x.optLong("uptime") else null,if(x.has("latencyMs")&&!x.isNull("latencyMs"))x.optInt("latencyMs") else null))}};done(MonitorState(summary.optString("level"),local.optInt("cpuPercent"),local.optInt("memoryPercent"),local.optInt("diskPercent"),if(local.isNull("temperature"))"--" else local.optDouble("temperature").toString(),summary.optInt("alertCount"),servers))}catch(e:Exception){done(MonitorState(level="critical",error="Respuesta no válida"))}}
}
object MonitorScheduler{fun start(c:Context){val req=PeriodicWorkRequestBuilder<MonitorWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("jr-monitor",ExistingPeriodicWorkPolicy.UPDATE,req)}}
class MonitorWorker(c:Context,p:WorkerParameters):Worker(c,p){override fun doWork():Result{MobileApi.init(applicationContext);if(!MobileApi.hasToken())return Result.success();val latch=java.util.concurrent.CountDownLatch(1);var ok=false;MobileApi.statusJson{j->if(j!=null){AlertNotifier.evaluate(applicationContext,j);ok=true};latch.countDown()};latch.await(20,TimeUnit.SECONDS);return if(ok)Result.success() else Result.retry()}}
object AlertNotifier{
 private const val CH="jr_alerts";fun channel(c:Context){if(Build.VERSION.SDK_INT>=26)(c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CH,"Alertas JR Server",NotificationManager.IMPORTANCE_HIGH))}
 fun evaluate(c:Context,j:JSONObject){val prefs=c.getSharedPreferences("jr_mobile",Context.MODE_PRIVATE);val active=j.optJSONArray("incidents");val now=mutableSetOf<String>();val info=mutableMapOf<String,Pair<String,String>>();if(active!=null)for(i in 0 until active.length()){val x=active.optJSONObject(i)?:continue;val key=x.optString("key");if(key.isNotBlank()){now.add(key);info[key]=Pair(x.optString("server","JR Server"),x.optString("title","Falla detectada"))}};val before=prefs.getStringSet("active_incidents",emptySet())?.toSet()?:emptySet();for(key in now-before){val x=info[key];notify(c,key.hashCode(),"JR Server · "+(x?.first?:"Alerta"),x?.second?:"Se detectó un nuevo incidente.")};for(key in before-now)notify(c,key.hashCode(),"JR Server · Recuperado","Un incidente activo fue resuelto.");prefs.edit().putStringSet("active_incidents",now).apply()}
 private fun notify(c:Context,id:Int,title:String,text:String){if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;channel(c);val intent=Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP);val pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=NotificationCompat.Builder(c,CH).setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).setAutoCancel(true).build();NotificationManagerCompat.from(c).notify(id,n)}
}
