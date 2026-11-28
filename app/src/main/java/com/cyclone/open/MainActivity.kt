package com.cyclone.open

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyclone.open.core.*
import com.cyclone.open.ui.GlassOptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val mint = Color(0xFFA8E5D2)
private val bg = Color(0xFF10151B)
private val panel = Color(0xFF202B35)
private val ink = Color(0xFFEAF5F1)
private val muted = Color(0xFFADBBC5)
private val scheme = darkColorScheme(primary=mint, onPrimary=Color(0xFF122D26), background=bg, onBackground=ink, surface=panel, onSurface=ink, secondary=Color(0xFFB4C8E3))
private enum class Page { PHONES, IMAGES, HEALTH }
private enum class DialogKind { CREATE, RENAME, REMOVE_PHONE, REMOVE_IMAGE, IMPORT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=scheme) { OpenScreen(this) } }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val shine = GlassOptics.power(GlassOptics.REST_X, GlassOptics.REST_Y)
    Column(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF293843),panel,bg)),shape)
        .border(1.dp, Brush.linearGradient(listOf(if(selected) mint else mint.copy(alpha=shine*0.36f),Color(0xFF3D4C57))),shape)
        .padding(18.dp), verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}

@Composable
private fun OpenScreen(activity: ComponentActivity) {
    val store=remember { RegistryStore.get(activity) }
    var registry by remember { mutableStateOf<Registry?>(null) }
    var page by remember { mutableStateOf(Page.PHONES) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var target by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var digest by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(15) }
    var memory by remember { mutableIntStateOf(3072) }
    var runtime by remember { mutableStateOf(RuntimeKind.LOCAL_VM) }
    var imageId by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val scope=rememberCoroutineScope()
    fun showError(e: Exception) {
        error = when (e) {
            is RegistryReadException -> "Saved records could not be read. Reload the registry to retry."
            is RegistryWriteException -> "The change could not be confirmed. Reload the registry before retrying."
            is IllegalArgumentException -> "Invalid configuration or checksum. Check the fields and try again."
            else -> "Operation failed. Existing records were not intentionally changed."
        }
    }
    fun refresh() { scope.launch { try { registry=withContext(Dispatchers.IO){store.read()}; error=null } catch(e: CancellationException){throw e} catch(e:Exception){showError(e)} } }
    LaunchedEffect(Unit) { refresh() }
    fun mutate(block: (Registry)->Registry) {
        if(busy) return
        busy=true;error=null
        scope.launch {
            try { registry=withContext(Dispatchers.IO){store.change(block)};dialog=null }
            catch(e:CancellationException){throw e}
            catch(e:Exception){showError(e)}
            finally{busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        if(chosen!=null){uri=chosen;name="Android image";digest="";version=15;dialog=DialogKind.IMPORT}
    }
    fun nativeSettings() {
        try { activity.startActivity(Intent("android.settings.USER_SETTINGS")) }
        catch(_:Exception) { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    Scaffold(
        containerColor=bg,
        bottomBar={ NavigationBar(containerColor=panel) {
            Page.entries.forEach { p ->
                val label=when(p){Page.PHONES->"Phones";Page.IMAGES->"Images";Page.HEALTH->"Health"}
                NavigationBarItem(selected=page==p,onClick={page=p},icon={Text(when(p){Page.PHONES->"▣";Page.IMAGES->"↓";Page.HEALTH->"◉"},fontSize=22.sp)},label={Text(label)})
            }
        }}
    ) { padding ->
        val r=registry
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text("CYCLONE  /  OPEN",color=mint,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp,fontSize=12.sp)
                Text(when(page){Page.PHONES->"Your phones";Page.IMAGES->"Image library";Page.HEALTH->"Node health"},fontSize=32.sp,fontWeight=FontWeight.Bold)
                Text("Android-first · Alpha 1",color=muted)
            }
            if(error!=null)item{GlassCard { Text(error!!,color=MaterialTheme.colorScheme.error); TextButton(onClick={error=null;refresh()}){Text("Reload registry")} }}
            if(r==null){item{Text("Registry unavailable or loading.",color=muted)}}
            else when(page) {
                Page.PHONES -> {
                    item { GlassCard(selected=r.selected==HOST_ID) {
                        Text("PHYSICAL HOST",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text(Build.MODEL,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+Build.VERSION.RELEASE+" · "+HostRuntime.status().detail,color=muted)
                        if(r.selected!=HOST_ID) OutlinedButton(enabled=!busy,onClick={mutate{Rules.select(it,HOST_ID)}}){Text("Select host")}
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Return home")}
                            OutlinedButton(onClick={nativeSettings()}){Text("Users")}
                        }
                    } }
                    item { Button(enabled=!busy&&r.images.isNotEmpty(),onClick={name="New phone";imageId=r.images.first().id;memory=3072;runtime=RuntimeKind.LOCAL_VM;dialog=DialogKind.CREATE},modifier=Modifier.fillMaxWidth()){Text("+  New phone draft")}
                        if(r.images.isEmpty())Text("Import a verified file reference in Images first.",color=muted) }
                    items(r.phones,key={it.id}){ p -> GlassCard(selected=r.selected==p.id) {
                        Text(if(r.selected==p.id)"SELECTED DRAFT" else "PHONE DRAFT",color=mint,fontSize=11.sp)
                        Text(p.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+r.images.first{it.id==p.imageId}.androidVersion+" · "+p.memoryMiB+" MiB · "+p.runtime.name,color=muted)
                        Text(UnavailableRuntime(p.runtime).status().detail,color=muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            TextButton(enabled=!busy,onClick={mutate{Rules.select(it,p.id)}}){Text("Select")}
                            TextButton(enabled=!busy,onClick={target=p.id;name=p.name;dialog=DialogKind.RENAME}){Text("Rename")}
                            TextButton(enabled=!busy,onClick={target=p.id;dialog=DialogKind.REMOVE_PHONE}){Text("Remove")}
                        }
                    }}
                }
                Page.IMAGES -> {
                    item { GlassCard { Text("Android 13–16",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("Import an authorized image file. A matching hash proves byte equality, not trusted provenance or bootability.",color=muted)
                        Button(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text("Import image")} }}
                    items(r.images,key={it.id}){ im -> GlassCard {
                        Text(im.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+im.androidVersion+" · "+im.bytes+" bytes",color=muted)
                        Text("SHA-256 matched on import · boot unverified",color=mint,fontSize=12.sp)
                        Text(im.sha256,fontSize=11.sp,color=muted)
                        TextButton(enabled=!busy,onClick={target=im.id;dialog=DialogKind.REMOVE_IMAGE}){Text("Remove reference")}
                    }}
                }
                Page.HEALTH -> {
                    item { GlassCard {
                        Text("Host",fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text(Build.MANUFACTURER+" "+Build.MODEL)
                        Text("Android "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT)
                        Text("ABI: "+Build.SUPPORTED_ABIS.joinToString(),color=muted)
                    } }
                    item{GlassCard{Text("Runtime readiness",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Local guest: not implemented");Text("Remote guest: not connected");Text("GPU/AVF: unverified");Text("No device certification claimed.",color=muted)}}
                    item{GlassCard{Text("Quick access",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Add Cyclone Open in Android Quick Settings. Each user must add its own tile; registry synchronization is not implemented.",color=muted)}}
                }
            }
            if(busy)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("Working…",color=muted)}
        }
    }
    val d=dialog
    val r=registry
    if(d!=null&&r!=null) AlertDialog(
        onDismissRequest={if(!busy)dialog=null},
        title={Text(when(d){DialogKind.CREATE->"Create phone draft";DialogKind.RENAME->"Rename phone";DialogKind.REMOVE_PHONE->"Remove phone draft?";DialogKind.REMOVE_IMAGE->"Remove image reference?";DialogKind.IMPORT->"Verify image"})},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
            if(d in listOf(DialogKind.CREATE,DialogKind.RENAME,DialogKind.IMPORT))OutlinedTextField(value=name,onValueChange={name=it},label={Text("Name")},singleLine=true,enabled=!busy)
            when(d) {
                DialogKind.CREATE -> {
                    Text("Select an image")
                    Column(Modifier.heightIn(max=150.dp).then(Modifier)) { r.images.take(8).forEach{im->Row(Modifier.fillMaxWidth().clickable(enabled=!busy){imageId=im.id}){RadioButton(selected=imageId==im.id,onClick={imageId=im.id},enabled=!busy);Text(im.name,modifier=Modifier.padding(top=14.dp))}} }
                    if(r.images.size>8)Text("First eight references shown; additional library paging is planned.",color=muted,fontSize=12.sp)
                    Row{listOf(2048,3072,4096).forEach{m->FilterChip(selected=memory==m,onClick={memory=m},label={Text((m/1024).toString()+" GB")},enabled=!busy)}}
                    Row{listOf(RuntimeKind.LOCAL_VM,RuntimeKind.REMOTE_VM).forEach{k->FilterChip(selected=runtime==k,onClick={runtime=k},label={Text(if(k==RuntimeKind.LOCAL_VM)"Local" else "Remote")},enabled=!busy)}}
                    Text("Draft only. No Android guest will start.",color=muted)
                }
                DialogKind.IMPORT -> {
                    Row{(13..16).forEach{v->FilterChip(selected=version==v,onClick={version=v},label={Text(v.toString())},enabled=!busy)}}
                    OutlinedTextField(value=digest,onValueChange={digest=it},label={Text("Expected SHA-256")},enabled=!busy)
                    Text("Use an independently trusted checksum. Large files may take time; file is never executed.",color=muted)
                }
                DialogKind.REMOVE_PHONE -> Text("Only this configuration is removed; no guest disk exists.")
                DialogKind.REMOVE_IMAGE -> Text("Remove the reference, not the source document. Referenced images cannot be removed.")
                DialogKind.RENAME -> Unit
            }
        }},
        dismissButton={TextButton(enabled=!busy,onClick={dialog=null}){Text("Cancel")}},
        confirmButton={Button(enabled=!busy,onClick={
            when(d){
                DialogKind.CREATE-> { val n=name;val i=imageId;val k=runtime;val m=memory;mutate{Rules.addPhone(it,n,i,k,m)} }
                DialogKind.RENAME-> {val id=target;val n=name;mutate{Rules.rename(it,id,n)}}
                DialogKind.REMOVE_PHONE->{val id=target;mutate{Rules.remove(it,id)}}
                DialogKind.REMOVE_IMAGE->{
                    val id=target;mutate{Rules.removeImage(it,id)}
                }
                DialogKind.IMPORT->{
                    val u=uri;val n=name;val v=version;val hash=digest
                    if(u!=null){
                        busy=true;error=null
                        scope.launch{
                            try{
                                registry=withContext(Dispatchers.IO){
                                    val label=Rules.label(n)
                                    val uriText = u.toString()
                                    require(store.read().images.none { it.uri == uriText })
                                    val check=ImageInspector.inspect(activity.contentResolver,u,hash)
                                    val image = ImageRecord(UUID.randomUUID().toString(), label, v, uriText, check.hash, check.bytes)
                                    val resolver = activity.contentResolver
                                    val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    synchronized(store) {
                                        require(store.read().images.none { it.uri == uriText })
                                        resolver.takePersistableUriPermission(u, grant)
                                        try {
                                            store.change { Rules.addImage(it, image) }
                                        } catch (e: Exception) {
                                            // A write can be unconfirmed. Keep access if any saved reference may still need it.
                                            val unused = runCatching { store.read().images.none { it.uri == uriText } }.getOrDefault(false)
                                            if (unused) runCatching { resolver.releasePersistableUriPermission(u, grant) }
                                            throw e
                                        }
                                    }
                                }
                                dialog=null
                            }catch(e:CancellationException){throw e}catch(e:Exception){showError(e);dialog=null}finally{busy=false}
                        }
                    }
                }
            }
        }){Text(if(d==DialogKind.IMPORT)"Verify & add" else if(d in listOf(DialogKind.REMOVE_IMAGE,DialogKind.REMOVE_PHONE))"Remove" else "Save")}}
    )
}
