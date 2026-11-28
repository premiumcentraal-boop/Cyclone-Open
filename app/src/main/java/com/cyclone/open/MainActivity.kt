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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyclone.open.core.*
import com.cyclone.open.ui.AuroraSheet
import com.cyclone.open.ui.DraftImagePicker
import com.cyclone.open.ui.GlassPanel
import com.cyclone.open.ui.OpenGlass
import com.cyclone.open.ui.PhoneProfileCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val mint = OpenGlass.Mint
private val bg = OpenGlass.Background
private val panel = OpenGlass.Panel
private val muted = OpenGlass.Muted
private enum class Page { PHONES, IMAGES, HEALTH }
private enum class DialogKind { CREATE, RENAME, REMOVE_PHONE, REMOVE_IMAGE, IMPORT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenGlass.Theme { OpenScreen(this) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenScreen(activity: ComponentActivity) {
    val store=remember { RegistryStore.get(activity) }
    val volumes = remember(store) { PhoneVolumeRepository(activity, store) }
    val switcher = remember { AtomicSwitchJournal.coordinator(activity) }
    var registry by remember { mutableStateOf<Registry?>(null) }
    var page by rememberSaveable { mutableStateOf(Page.PHONES) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<DialogKind?>(null) }
    var target by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var digest by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableIntStateOf(15) }
    var memory by rememberSaveable { mutableIntStateOf(3072) }
    var runtime by rememberSaveable { mutableStateOf(RuntimeKind.LOCAL_VM) }
    var imageId by rememberSaveable { mutableStateOf("") }
    var imageQuery by rememberSaveable { mutableStateOf("") }
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var auroraOpen by rememberSaveable { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun showError(e: Exception) {
        error = when (e) {
            is InsufficientProfileStorageException -> e.message
            is RegistryReadException -> "Saved records could not be read. Reload the registry to retry."
            is RegistryWriteException -> "The change could not be confirmed. Reload the registry before retrying."
            is IllegalArgumentException -> "Invalid configuration or checksum. Check the fields and try again."
            else -> "Operation failed. Existing records were not intentionally changed."
        }
    }
    fun refresh() { scope.launch { try { registry=withContext(Dispatchers.IO){volumes.open()}; error=null } catch(e: CancellationException){throw e} catch(e:Exception){showError(e)} } }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(switcher) {
        try {
            withContext(Dispatchers.IO) { switcher.recover() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            error = "Saved switch state could not be recovered. Android Home is available."
        }
    }
    fun commit(operation: () -> Registry) {
        if(busy) return
        busy=true;error=null
        scope.launch {
            try { registry=withContext(Dispatchers.IO) { operation() };dialog=null }
            catch(e:CancellationException){throw e}
            catch(e:Exception){showError(e)}
            finally{busy=false}
        }
    }
    fun mutate(block: (Registry)->Registry) = commit { store.change(block) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        if(chosen!=null){uri=chosen;name="Android image";digest="";version=15;dialog=DialogKind.IMPORT}
    }
    fun nativeSettings() {
        try { activity.startActivity(Intent("android.settings.USER_SETTINGS")) }
        catch(_:Exception) { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    fun returnHome() = scope.launch {
        try {
            withContext(Dispatchers.IO) { switcher.switchTo(PhoneTarget.Host) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            // Android Home remains available even if the app's journal cannot be saved.
        }
        activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Scaffold(
        containerColor=bg,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { auroraOpen = true }) { Text("Aurora") }
        },
        bottomBar={ NavigationBar(containerColor=panel) {
            Page.entries.forEach { p ->
                val label=when(p){Page.PHONES->"Phones";Page.IMAGES->"Images";Page.HEALTH->"Health"}
                NavigationBarItem(selected=page==p,onClick={page=p},icon={Text(when(p){Page.PHONES->"▣";Page.IMAGES->"↓";Page.HEALTH->"◉"},Modifier.clearAndSetSemantics { },fontSize=22.sp)},label={Text(label)})
            }
        }}
    ) { padding ->
        val r=registry
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 88.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text("CYCLONE  /  OPEN",color=mint,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp,fontSize=12.sp)
                Text(when(page){Page.PHONES->"Your phones";Page.IMAGES->"Image library";Page.HEALTH->"Node health"},fontSize=32.sp,fontWeight=FontWeight.Bold)
                Text("Android-first · Alpha 1",color=muted)
            }
            if(error!=null)item{GlassPanel { Text(error!!,color=MaterialTheme.colorScheme.error); TextButton(onClick={error=null;refresh()}){Text("Reload registry")} }}
            if(r==null){item{Text("Registry unavailable or loading.",color=muted)}}
            else when(page) {
                Page.PHONES -> {
                    item { GlassPanel(selected=r.selected==HOST_ID) {
                        Text("PHYSICAL HOST",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text(Build.MODEL,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+Build.VERSION.RELEASE+" · "+HostRuntime.status().detail,color=muted)
                        if(r.selected!=HOST_ID) OutlinedButton(enabled=!busy,onClick={mutate{Rules.select(it,HOST_ID)}}){Text("Select host")}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={returnHome()}){Text("Return home")}
                            OutlinedButton(onClick={nativeSettings()}){Text("Users")}
                        }
                    } }
                    item { Button(enabled=!busy&&r.images.isNotEmpty(),onClick={name="New phone";imageId=r.images.first().id;imageQuery="";memory=3072;runtime=RuntimeKind.LOCAL_VM;dialog=DialogKind.CREATE},modifier=Modifier.fillMaxWidth()){Text("New phone draft")}
                        if(r.images.isEmpty())Text("Import a verified file reference in Images first.",color=muted) }
                    items(r.phones,key={it.id}){ p ->
                        PhoneProfileCard(
                            p, r.images.first { it.id == p.imageId }.androidVersion, r.selected == p.id, busy,
                            onSelect = { mutate { Rules.select(it, p.id) } },
                            onRename = { target = p.id; name = p.name; dialog = DialogKind.RENAME },
                            onRemove = { target = p.id; dialog = DialogKind.REMOVE_PHONE }
                        )
                    }
                }
                Page.IMAGES -> {
                    item { GlassPanel { Text("Android 13–16",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("Import an authorized image file. A matching hash proves byte equality, not trusted provenance or bootability.",color=muted)
                        Button(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text("Import image")} }}
                    items(r.images,key={it.id}){ im -> GlassPanel {
                        Text(im.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+im.androidVersion+" · "+im.bytes+" bytes",color=muted)
                        Text("SHA-256 matched on import · boot unverified",color=mint,fontSize=12.sp)
                        Text(im.sha256,fontSize=11.sp,color=muted)
                        TextButton(enabled=!busy,onClick={target=im.id;dialog=DialogKind.REMOVE_IMAGE}){Text("Remove reference")}
                    }}
                }
                Page.HEALTH -> {
                    item { GlassPanel {
                        Text("Host",fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text(Build.MANUFACTURER+" "+Build.MODEL)
                        Text("Android "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT)
                        Text("ABI: "+Build.SUPPORTED_ABIS.joinToString(),color=muted)
                    } }
                    item{GlassPanel{Text("Runtime readiness",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Local and remote guests: not implemented");Text("GPU/AVF: unverified")}}
                    item{GlassPanel{Text("Quick access",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Add Cyclone Open in Android Quick Settings for each Android user.",color=muted)}}
                }
            }
            if(busy)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("Working…",color=muted)}
        }
    }
    if (auroraOpen) {
        AuroraSheet(
            registry, busy,
            dismiss = { auroraOpen = false },
            openAndroidHome = { auroraOpen = false; returnHome() },
            selectDraft = { id -> auroraOpen = false; mutate { Rules.select(it, id) } }
        )
    }
    val d=dialog
    val r=registry
    if(d!=null&&r!=null) AlertDialog(
        onDismissRequest={if(!busy)dialog=null},
        title={Text(when(d){DialogKind.CREATE->"Create phone draft";DialogKind.RENAME->"Rename phone";DialogKind.REMOVE_PHONE->"Remove phone draft?";DialogKind.REMOVE_IMAGE->"Remove image reference?";DialogKind.IMPORT->"Verify image"})},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().verticalScroll(rememberScrollState())){
            if(d in listOf(DialogKind.CREATE,DialogKind.RENAME,DialogKind.IMPORT))OutlinedTextField(value=name,onValueChange={name=it},label={Text("Name")},singleLine=true,enabled=!busy)
            when(d) {
                DialogKind.CREATE -> {
                    DraftImagePicker(r.images, imageQuery, imageId, !busy, { imageQuery = it }, { imageId = it })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(2048,3072,4096).forEach{m->FilterChip(selected=memory==m,onClick={memory=m},label={Text((m/1024).toString()+" GiB")},enabled=!busy)} }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(RuntimeKind.LOCAL_VM,RuntimeKind.REMOTE_VM).forEach{k->FilterChip(selected=runtime==k,onClick={runtime=k},label={Text(if(k==RuntimeKind.LOCAL_VM)"Local" else "Remote")},enabled=!busy)} }
                    Text("Draft only. No Android guest will start.",color=muted)
                }
                DialogKind.IMPORT -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (13..16).forEach{v->FilterChip(selected=version==v,onClick={version=v},label={Text(v.toString())},enabled=!busy)} }
                    OutlinedTextField(value=digest,onValueChange={digest=it},label={Text("Expected SHA-256")},enabled=!busy)
                    Text("Use an independently trusted checksum. Large files may take time; file is never executed.",color=muted)
                }
                DialogKind.REMOVE_PHONE -> Text("Removes this configuration and retains its profile files.")
                DialogKind.REMOVE_IMAGE -> Text("Remove the reference, not the source document. Referenced images cannot be removed.")
                DialogKind.RENAME -> Unit
            }
            if (d != DialogKind.IMPORT && error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }},
        dismissButton={TextButton(enabled=!busy,onClick={dialog=null}){Text("Cancel")}},
        confirmButton={Button(enabled=!busy,onClick={
            when(d){
                DialogKind.CREATE-> { val n=name;val i=imageId;val k=runtime;val m=memory;commit{volumes.create(n,i,k,m)} }
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
