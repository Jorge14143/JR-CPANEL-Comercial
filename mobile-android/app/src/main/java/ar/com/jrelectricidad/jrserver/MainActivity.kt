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

private val Navy=Color(0xFF06111F)
private val Panel=Color(0xFF0D1B2B)
private val Panel2=Color(0xFF12243A)
private val Blue=Color(0xFF1DA1FF)
private val Cyan=Color(0xFF20D7FF)
private val Green=Color(0xFF25E58B)
private val Orange=Color(0xFFFFA24C)
private val Purple=Color(0xFFB96CFF)
private val Red=Color(0xFFFF5267)
private val Muted=Color(0xFF9FB0C4)

data class ServerState(val id:String,val name:String,val online:Boolean,val level:String,val host:String,val cpu:Int?=null,val ram:Int?=null,val disk:Int?=null,val temp:String?=null,val uptime:Long?=null,val latency:Int?=null)
data class IncidentState(val event:String,val server:String,val title:String,val level:String,val time:String)
data class ServiceState(val name:String,val status:String,val cpu:Int,val memory:Long,val restarts:Int)
data class SystemServiceState(val name:String,val label:String,val state:String,val active:Boolean,val enabled:String,val controllable:Boolean)
data class MobileOverview(val rx:Long=0,val tx:Long=0,val iface:String="--",val maintenance:Boolean=false,val backup:String?=null,val webOk:Int=0,val webTotal:Int=0,val latency:Int=0,val sslDays:Int?=null)
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
 var state by remember{mutableStateOf(MonitorState())};var overview by remember{mutableStateOf(MobileOverview())};var incidents by remember{mutableStateOf(emptyList<IncidentState>())};var services by remember{mutableStateOf(emptyList<ServiceState>())};var systemServices by remember{mutableStateOf(emptyList<SystemServiceState>())}
 var tab by remember{mutableIntStateOf(0)};var paired by remember{mutableStateOf(MobileApi.hasToken())};var selected by remember{mutableStateOf<ServerState?>(null)};var refreshing by remember{mutableStateOf(false)}
 fun refresh(){refreshing=true;MobileApi.load{v->(context as? Activity)?.runOnUiThread{state=v;refreshing=false}};MobileApi.incidents{v->(context as? Activity)?.runOnUiThread{incidents=v}};MobileApi.services{v->(context as? Activity)?.runOnUiThread{services=v}};MobileApi.systemServices{v->(context as? Activity)?.runOnUiThread{systemServices=v}};MobileApi.overview{v->(context as? Activity)?.runOnUiThread{overview=v}}}
 if(!paired){PairScreen{paired=true};return}
 LaunchedEffect(Unit){refresh()}
 if(selected!=null){ServerDetail(selected!!,services,systemServices,overview,incidents,onBack={selected=null},onRefresh={refresh()});return}
 Scaffold(containerColor=Navy,bottomBar={BottomNav(tab,state.alerts){tab=it}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){
   when(tab){
    1->IncidentList(incidents)
    2->ActionsScreen(overview,onRefresh={refresh()})
    3->IncidentHistory(incidents)
    else->Dashboard(state,overview,refreshing,onRefresh={refresh()},onServer={selected=it},onAlerts={tab=1})
   }
  }
 }
}

@Composable fun BottomNav(tab:Int,alerts:Int,onTab:(Int)->Unit){
 val icons=listOf(Icons.Default.Home,Icons.Default.Notifications,Icons.Default.Bolt,Icons.Default.History)
 val labels=listOf("Inicio","Alertas","Acciones","Historial")
 NavigationBar(containerColor=Color(0xFF071421),tonalElevation=12.dp){
  labels.indices.forEach{i->NavigationBarItem(selected=tab==i,onClick={onTab(i)},icon={BadgedBox(badge={if(i==1&&alerts>0)Badge(containerColor=Red){Text(alerts.toString())}}){Icon(icons[i],labels[i],modifier=Modifier.size(23.dp))}},label={Text(labels[i],fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Blue,selectedTextColor=Blue,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=Color(0xFF10263A)))}
 }
}

@Composable fun Header(title:String,subtitle:String,action:String?=null,onAction:(()->Unit)?=null){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(36.dp).background(Blue,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){Text("JR",fontWeight=FontWeight.Bold,color=Color.White)};Spacer(Modifier.width(10.dp));Column{Text(title,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=12.sp)}}
  if(action!=null)TextButton(onClick={onAction?.invoke()}){Text(action,color=Cyan)}
 }
}

@Composable fun Dashboard(state:MonitorState,overview:MobileOverview,refreshing:Boolean,onRefresh:()->Unit,onServer:(ServerState)->Unit,onAlerts:()->Unit){
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=14.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("JR",fontSize=32.sp,fontWeight=FontWeight.Black,color=Color.White);Spacer(Modifier.width(10.dp));Text("Server Mobile",fontSize=16.sp,fontWeight=FontWeight.Medium,color=Color.White);Spacer(Modifier.weight(1f));BadgedBox(badge={if(state.alerts>0)Badge(containerColor=Red){Text(state.alerts.toString())}}){IconButton(onClick=onAlerts){Icon(Icons.Default.Notifications,"Alertas",tint=Color(0xFFD9E6F5))}};IconButton(onClick=onRefresh){Icon(Icons.Default.Settings,"Actualizar",tint=Color(0xFFD9E6F5))}}}
  item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("Mis servidores",fontSize=22.sp,fontWeight=FontWeight.Bold);FilledIconButton(onClick=onRefresh,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Panel2)){Icon(Icons.Default.Add,"Actualizar",tint=Color.White)}}}
  if(state.servers.isEmpty()) item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Text("Esperando servidores…",Modifier.padding(20.dp),color=Muted)}}
  items(state.servers){ServerCard(it){onServer(it)}}
  item{SectionTitle("SALUD GENERAL")}
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniMetric("WEBS",overview.webOk.toString()+"/"+overview.webTotal,if(overview.webOk==overview.webTotal&&overview.webTotal>0)Green else Orange,Modifier.weight(1f));MiniMetric("LATENCIA",overview.latency.toString()+" ms",Blue,Modifier.weight(1f));MiniMetric("SSL",overview.sslDays?.let{it.toString()+" días"}?:"--",if((overview.sslDays?:99)<15)Orange else Green,Modifier.weight(1f))}}
 }
}

@Composable fun ActionsScreen(overview:MobileOverview,onRefresh:()->Unit){
 val context=LocalContext.current;var ask by remember{mutableStateOf<String?>(null)};var msg by remember{mutableStateOf<String?>(null)}
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ArrowBack,"Volver",tint=Color.White);Spacer(Modifier.width(18.dp));Text("Acciones rápidas",fontSize=26.sp,fontWeight=FontWeight.Bold)}}
  item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(Panel2,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Dns,"Servidor",tint=Color.White)};Spacer(Modifier.width(12.dp));Column{Text("jrelectricidad.com.ar",fontWeight=FontWeight.Bold,fontSize=16.sp);Text("185.171.244.12",color=Muted,fontSize=12.sp)}}}
  item{ActionGridRow(ActionSpec(Icons.Default.PowerSettingsNew,"Reiniciar servidor",Red,"reboot"),ActionSpec(Icons.Default.Build,"Modo mantenimiento",Orange,if(overview.maintenance)"maintenance-off" else "maintenance-on")){ask=it}}
  item{ActionGridRow(ActionSpec(Icons.Default.CleaningServices,"Limpiar caché",Purple,"nginx-reload"),ActionSpec(Icons.Default.Refresh,"Reiniciar servicios",Blue,"nginx-reload")){ask=it}}
  item{ActionGridRow(ActionSpec(Icons.Default.CloudUpload,"Deploy Now",Green,"deploy-prod"),ActionSpec(Icons.Default.Storage,"Ejecutar backup",Color(0xFFFFC33B),"backup")){ask=it}}
  item{Card(Modifier.fillMaxWidth().clickable{MobileApi.quickLogs{m->(context as Activity).runOnUiThread{msg=m}}},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF351274)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(Purple))){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Description,"Logs",tint=Purple,modifier=Modifier.size(36.dp));Spacer(Modifier.width(16.dp));Text("Ver logs del incidente",fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.weight(1f));Icon(Icons.Default.ChevronRight,"Abrir")}}}
  msg?.let{item{Text(it,color=Muted,fontSize=11.sp)}}
 }
 if(ask!=null)AlertDialog(onDismissRequest={ask=null},title={Text("Confirmar acción")},text={Text("La acción se ejecutará en el servidor.")},confirmButton={TextButton(onClick={val x=ask!!;ask=null;authenticate(context as FragmentActivity){if(x=="deploy-prod")MobileApi.deploy("prod"){_,_->onRefresh()} else MobileApi.quickAction(x){_,_->onRefresh()}}}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={ask=null}){Text("CANCELAR")}})
}
data class ActionSpec(val icon:androidx.compose.ui.graphics.vector.ImageVector,val label:String,val color:Color,val action:String)
@Composable fun ActionGridRow(a:ActionSpec,b:ActionSpec,onAction:(String)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){MockAction(a,Modifier.weight(1f),onAction);MockAction(b,Modifier.weight(1f),onAction)}}
@Composable fun MockAction(x:ActionSpec,modifier:Modifier,onAction:(String)->Unit){Card(modifier.height(150.dp).clickable{onAction(x.action)},shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=x.color.copy(alpha=.22f)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(x.color))){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(x.icon,x.label,tint=x.color,modifier=Modifier.size(46.dp));Spacer(Modifier.height(18.dp));Text(x.label,fontWeight=FontWeight.Bold,fontSize=15.sp)}}}

@Composable fun IncidentHistory(data:List<IncidentState>){
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Historial",fontSize=25.sp,fontWeight=FontWeight.Bold)}
  if(data.isEmpty()) item{Text("No hay incidentes registrados",color=Muted)}
  items(data){x->Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(x.event=="resolved")Icons.Default.CheckCircle else Icons.Default.Warning,"Estado",tint=if(x.event=="resolved")Green else Orange);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.Bold);Text(x.server+" · "+x.time,color=Muted,fontSize=11.sp)}}}}
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
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("JR SERVER","Servidores")};item{Card(colors=CardDefaults.cardColors(containerColor=Panel2),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(14.dp).background(if(servers.all{it.online})Green else Orange,CircleShape));Spacer(Modifier.width(12.dp));Column{Text("SERVIDORES",fontWeight=FontWeight.Bold);Text("${servers.count{it.online}} en línea / ${servers.count{!it.online}} fuera de servicio",color=Muted)}}}};items(servers){ServerCard(it){open(it)}}}
}

@Composable fun ServerCard(s:ServerState,onClick:()->Unit={}){
 val accent=if(s.online)Green else Red
 Card(Modifier.fillMaxWidth().clickable{onClick()},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0C1D2D)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(accent.copy(alpha=.55f)))){
  Column(Modifier.padding(16.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Blue.copy(alpha=.18f),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Dns,"Servidor",tint=Blue)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold,fontSize=15.sp);Text(s.host,color=Muted,fontSize=11.sp)};Surface(shape=RoundedCornerShape(12.dp),color=accent.copy(alpha=.18f)){Text(if(s.online)"ONLINE" else "OFFLINE",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=accent,fontWeight=FontWeight.Bold,fontSize=10.sp)}}
   if(s.online){Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){ServerStat("CPU",s.cpu,Blue,Modifier.weight(1f));ServerStat("RAM",s.ram,Green,Modifier.weight(1f));ServerStat("DISCO",s.disk,Orange,Modifier.weight(1f));Icon(Icons.Default.ArrowForward,"Abrir",tint=Muted,modifier=Modifier.align(Alignment.CenterVertically))}}
  }
 }
}
@Composable fun ServerStat(label:String,value:Int?,color:Color,modifier:Modifier){Column(modifier){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(color,RoundedCornerShape(3.dp)));Spacer(Modifier.width(5.dp));Text(label,color=Muted,fontSize=9.sp)};Text(value?.let{it.toString()+"%"}?:"--",fontWeight=FontWeight.Bold,fontSize=12.sp)}}

@Composable fun ServerDetail(server:ServerState,services:List<ServiceState>,systemServices:List<SystemServiceState>,overview:MobileOverview,incidents:List<IncidentState>,onBack:()->Unit,onRefresh:()->Unit){
 var page by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize()){
  ServerTop(server,onBack)
  ServerTabs(page){page=it}
  Box(Modifier.weight(1f)){when(page){0->SummaryTab(server,overview);1->ServicesTab(services,systemServices,onRefresh);2->NetworkTab(overview);else->LogsTab(incidents)}}
 }
}
@Composable fun ServerTop(server:ServerState,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(16.dp,14.dp,16.dp,8.dp),verticalAlignment=Alignment.CenterVertically){FilledIconButton(onClick=onBack,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Panel2)){Icon(Icons.Default.ArrowBack,"Volver")};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(server.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(server.host,color=Muted,fontSize=11.sp)};Column(horizontalAlignment=Alignment.End){Surface(shape=RoundedCornerShape(14.dp),color=Green.copy(alpha=.25f)){Text(if(server.online)"ONLINE" else "OFFLINE",Modifier.padding(horizontal=12.dp,vertical=5.dp),color=if(server.online)Green else Red,fontWeight=FontWeight.Bold,fontSize=11.sp)};Text("Hace 2 min",color=Muted,fontSize=10.sp)};Icon(Icons.Default.MoreVert,"Opciones",tint=Muted)}
}
@Composable fun ServerTabs(page:Int,set:(Int)->Unit){val names=listOf("Resumen","Servicios","Red","Logs");Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).background(Color(0xFF071421),RoundedCornerShape(14.dp))){names.forEachIndexed{i,n->Box(Modifier.weight(1f).clickable{set(i)}.then(if(page==i)Modifier.background(Color(0xFF063A76),RoundedCornerShape(12.dp)) else Modifier).padding(vertical=14.dp),contentAlignment=Alignment.Center){Text(n,color=if(page==i)Cyan else Color(0xFFC4D0E2),fontWeight=if(page==i)FontWeight.Bold else FontWeight.Normal)}}}}
@Composable fun SummaryTab(s:ServerState,o:MobileOverview){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricBar("CPU",s.cpu?:0,Green,Modifier.weight(1f));MetricBar("RAM",s.ram?:0,Blue,Modifier.weight(1f));MetricBar("DISCO",s.disk?:0,Orange,Modifier.weight(1f))}};item{NetworkChart(o)};item{Text("Estado general",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{StatusRows(o)}}}
@Composable fun MetricBar(label:String,value:Int,color:Color,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(13.dp),colors=CardDefaults.cardColors(containerColor=Panel),border=CardDefaults.outlinedCardBorder()){Column(Modifier.padding(14.dp)){Text(label,color=Muted,fontSize=12.sp);Text(value.toString()+"%",fontSize=23.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth().height(8.dp),color=color,trackColor=Panel2,strokeCap=StrokeCap.Round)}}}
@Composable fun NetworkChart(o:MobileOverview){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Panel),border=CardDefaults.outlinedCardBorder()){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth()){Text("Tráfico de red",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("↓ "+formatRate(o.rx),color=Cyan);Spacer(Modifier.width(14.dp));Text("↑ "+formatRate(o.tx),color=Red)};Spacer(Modifier.height(12.dp));Canvas(Modifier.fillMaxWidth().height(120.dp)){val p=Path();val vals=listOf(.62f,.48f,.58f,.35f,.70f,.52f,.60f,.45f,.73f,.42f,.55f,.30f);vals.forEachIndexed{i,v->val x=size.width*i/(vals.size-1);val y=size.height*v;if(i==0)p.moveTo(x,y) else p.lineTo(x,y)};drawPath(p,Cyan,style=Stroke(width=4f,cap=StrokeCap.Round));val q=Path();vals.forEachIndexed{i,v->val x=size.width*i/(vals.size-1);val y=size.height*(.78f-v*.25f);if(i==0)q.moveTo(x,y) else q.lineTo(x,y)};drawPath(q,Green,style=Stroke(width=3f,cap=StrokeCap.Round))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Hace 5 min",color=Muted,fontSize=10.sp);Text("Ahora",color=Muted,fontSize=10.sp)}}}}
@Composable fun StatusRows(o:MobileOverview){Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column{StatusLine(Icons.Default.Language,"HTTP / Web",if(o.webTotal>0)o.webOk.toString()+"/"+o.webTotal else "--",if(o.webOk==o.webTotal&&o.webTotal>0)Green else Orange,o.latency.toString()+" ms");StatusLine(Icons.Default.Lock,"SSL",o.sslDays?.let{"Válido"}?:"--",Green,o.sslDays?.let{it.toString()+" días"}?:"--");StatusLine(Icons.Default.Storage,"Base de datos","Online",Green,"");StatusLine(Icons.Default.Dns,"PM2 (Panel)","Online",Green,"1 procesos");StatusLine(Icons.Default.Public,"Nginx","Online",Green,"")}}}
@Composable fun StatusLine(icon:androidx.compose.ui.graphics.vector.ImageVector,name:String,state:String,color:Color,detail:String){Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,name,tint=color,modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(name,Modifier.weight(1f));Surface(shape=RoundedCornerShape(10.dp),color=color.copy(alpha=.15f)){Text(state,Modifier.padding(horizontal=9.dp,vertical=3.dp),color=color,fontSize=11.sp)};Spacer(Modifier.width(12.dp));Text(detail,color=Muted,fontSize=10.sp)}}
@Composable fun ServicesTab(services:List<ServiceState>,system:List<SystemServiceState>,onRefresh:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(system){SystemServiceCard(it,onRefresh)};items(services){ServiceCard(it,onRefresh)};item{Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Refresh,"Reiniciar");Spacer(Modifier.width(8.dp));Text("Reiniciar todos los servicios")}}}}
@Composable fun NetworkTab(o:MobileOverview){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{NetworkChart(o)};item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Uso de ancho de banda",fontWeight=FontWeight.Bold,fontSize=18.sp);Spacer(Modifier.height(14.dp));Row{Column(Modifier.weight(1f)){Text("↓ Descarga",color=Green);Text(formatRate(o.rx),fontSize=25.sp,fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f)){Text("↑ Subida",color=Red);Text(formatRate(o.tx),fontSize=25.sp,fontWeight=FontWeight.Bold)}}}};item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Conexiones activas",fontWeight=FontWeight.Bold);Text("--",fontSize=34.sp,fontWeight=FontWeight.Bold,color=Muted)}}};item{Text("Latencia y respuesta",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniMetric("Ping",o.latency.toString()+" ms",Green,Modifier.weight(1f));MiniMetric("Jitter","--",Blue,Modifier.weight(1f));MiniMetric("Pérdida","--",Orange,Modifier.weight(1f))}};item{Text("Interfaces de red",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{StatusLine(Icons.Default.Lan,o.iface,"Activa",Green,formatRate(o.rx)+" ↓  "+formatRate(o.tx)+" ↑")}}}}
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
@Composable fun AlertCard(x:IncidentState){val critical=x.level.contains("crit",true)||x.title.contains("502");val info=x.event=="resolved"||x.title.contains("backup",true)||x.title.contains("reinici",true);val color=if(info)Blue else if(critical)Red else Orange;Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.13f)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(color))){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(color,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(if(info)Icons.Default.Info else Icons.Default.Warning,"Alerta",tint=Color.White)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(x.server,color=Muted,fontSize=12.sp);Text(x.time,color=Muted,fontSize=11.sp)};Icon(Icons.Default.ChevronRight,"Abrir",tint=Muted)}}}

@Composable fun SettingsScreen(onDisconnect:()->Unit){
 var confirm by remember{mutableStateOf(false)}
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("JR SERVER","Ajustes")};item{SectionTitle("CONFIGURACIÓN DE LA APP")};item{SettingCard("▣","URL del panel",MobileApi.panelUrl())};item{SettingCard("↻","Actualización","Segundo plano cada 15 minutos")};item{SectionTitle("SEGURIDAD")};item{Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth().clickable{confirm=true}){Text("⇥  Desvincular dispositivo",Modifier.padding(18.dp),color=Red,fontWeight=FontWeight.Bold)}};item{SectionTitle("INFORMACIÓN")};item{SettingCard("ⓘ","Versión de la app","v1.0.0")}}
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Desvincular teléfono")},text={Text("La app dejará de consultar este JR Server Panel.")},confirmButton={TextButton(onClick=onDisconnect){Text("DESVINCULAR")}},dismissButton={TextButton(onClick={confirm=false}){Text("CANCELAR")}})
}
@Composable fun SettingCard(icon:String,title:String,sub:String){Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,color=Blue,fontSize=22.sp);Spacer(Modifier.width(12.dp));Column{Text(title,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=12.sp)}}}}

@Composable fun MaintenanceVisual(s:ServerState){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Mantenimiento",fontSize=26.sp,fontWeight=FontWeight.Bold)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricBar("CPU",s.cpu?:0,Green,Modifier.weight(1f));MetricBar("RAM",s.ram?:0,Blue,Modifier.weight(1f));MetricBar("DISCO",s.disk?:0,Orange,Modifier.weight(1f))}};item{Text("Temperatura del sistema",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniMetric("CPU",s.temp?.plus("°C")?:"--",Green,Modifier.weight(1f));MiniMetric("Placa base","--",Blue,Modifier.weight(1f));MiniMetric("Fuente","--",Orange,Modifier.weight(1f))}};item{Text("Ventiladores",fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Text("Sin datos de sensores disponibles",color=Muted)}}}

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
 fun overview(done:(MobileOverview)->Unit)=request("/api/mobile/v1/overview"){code,raw->if(code !in 200..299){done(MobileOverview());return@request};try{val j=JSONObject(raw);val n=j.getJSONObject("network");val b=j.optJSONObject("backup");val h=j.optJSONArray("health");var webOk=0;var latency=0;if(h!=null)for(i in 0 until h.length()){val x=h.getJSONObject(i);if(x.optBoolean("ok"))webOk++;latency+=x.optInt("latencyMs")};if(h!=null&&h.length()>0)latency/=h.length();val c=j.optJSONArray("certificates");var ssl:Int?=null;if(c!=null)for(i in 0 until c.length()){val d=c.getJSONObject(i).optInt("days",-1);if(d>=0&&(ssl==null||d<ssl!!))ssl=d};done(MobileOverview(n.optLong("rxPerSec"),n.optLong("txPerSec"),n.optString("interface","--"),j.optBoolean("maintenance"),b?.optJSONObject("last")?.optString("time"),webOk,h?.length()?:0,latency,ssl))}catch(e:Exception){done(MobileOverview())}}
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
