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
import androidx.lifecycle.ViewModelProvider
import com.cyclone.open.core.*
import com.cyclone.open.ui.AuroraSheet
import com.cyclone.open.ui.DraftImagePicker
import com.cyclone.open.ui.DraftReviewPanel
import com.cyclone.open.ui.GlassPanel
import com.cyclone.open.ui.OpenGlass
import com.cyclone.open.ui.OperationHistoryPanel
import com.cyclone.open.ui.DraftHealthPanel
import com.cyclone.open.ui.PhoneProfileCard
import com.cyclone.open.ui.ReferenceChoices
import com.cyclone.open.ui.RuntimeDoctorPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val mint = OpenGlass.Mint
private val bg = OpenGlass.Background
private val panel = OpenGlass.Panel
private val muted = OpenGlass.Muted
private enum class Page { PHONES, IMAGES, HEALTH }
private enum class DialogKind { CREATE, RESTORE, REMOVE_BACKUP, RENAME, REMOVE_PHONE, REMOVE_IMAGE, IMPORT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = ViewModelProvider(this)[OpenRegistryViewModel::class.java]
        setContent { OpenGlass.Theme { OpenScreen(this, controller) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenScreen(activity: ComponentActivity, controller: OpenRegistryViewModel) {
    val switcher = remember { AtomicSwitchJournal.coordinator(activity) }
    val registry by controller.registry.collectAsState()
    val operation by controller.operation.collectAsState()
    val catalogState by controller.catalog.collectAsState()
    val catalog = (catalogState as? CatalogState.Loaded)?.catalog
    val draftReview by controller.review.collectAsState()
    val hostObservation by controller.hostObservation.collectAsState()
    val draftHealth by controller.draftHealth.collectAsState()
    val backups by controller.backups.collectAsState()
    val history by controller.history.collectAsState()
    val busy = operation.busy
    var page by rememberSaveable { mutableStateOf(Page.PHONES) }
    var switchError by remember { mutableStateOf<String?>(null) }
    val error = operation.error ?: switchError
    var dialog by rememberSaveable { mutableStateOf<DialogKind?>(null) }
    var target by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var digest by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableIntStateOf(15) }
    var memory by rememberSaveable { mutableIntStateOf(3072) }
    var runtime by rememberSaveable { mutableStateOf(RuntimeKind.LOCAL_VM) }
    var imageId by rememberSaveable { mutableStateOf("") }
    var referenceId by rememberSaveable { mutableStateOf("") }
    var imageQuery by rememberSaveable { mutableStateOf("") }
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var auroraOpen by rememberSaveable { mutableStateOf(false) }
    var restoreTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var acknowledgedChange by rememberSaveable { mutableLongStateOf(operation.completedChanges) }
    val reference = catalog?.references?.firstOrNull { it.key.id == referenceId }
    val scope=rememberCoroutineScope()
    fun closeDialog() {
        if (dialog == DialogKind.CREATE || dialog == DialogKind.RESTORE) controller.clearDraftReview()
        dialog = null
    }
    LaunchedEffect(operation.completedChanges) {
        if (operation.completedChanges != acknowledgedChange) {
            if (operation.completedChanges > acknowledgedChange) dialog = null
            acknowledgedChange = operation.completedChanges
        }
    }
    LaunchedEffect(switcher) {
        try {
            withContext(Dispatchers.IO) { switcher.recover() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            switchError = "Saved switch state could not be recovered. Android Home is available."
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        if(chosen!=null){uri=chosen;name="Android image";digest="";version=15;dialog=DialogKind.IMPORT}
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        val id = restoreTarget
        restoreTarget = null
        if (chosen != null && id != null) controller.restoreImageAccess(id, chosen)
    }
    fun nativeSettings() {
        try { activity.startActivity(Intent("android.settings.USER_SETTINGS")) }
        catch(_:Exception) { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    fun returnHome() = scope.launch {
        try {
            withContext(Dispatchers.IO) {
                LogBus.get(activity).track(ControllerAction.RETURN_HOME) {
                    if (switcher.switchTo(PhoneTarget.Host) is RuntimeStepResult.Rejected) {
                        throw java.io.IOException("The host lease could not be confirmed.")
                    }
                }
            }
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
                NavigationBarItem(selected=page==p,onClick={
                    page=p
                    if (p == Page.HEALTH) controller.refreshHealth()
                },icon={Text(when(p){Page.PHONES->"▣";Page.IMAGES->"↓";Page.HEALTH->"◉"},Modifier.clearAndSetSemantics { },fontSize=22.sp)},label={Text(label)})
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
            if(error!=null)item{GlassPanel { Text(error,color=MaterialTheme.colorScheme.error); TextButton(enabled = !busy, onClick={switchError=null;controller.refresh()}){Text("Reload registry")} }}
            operation.notice?.let { notice -> item { Text(notice, color = mint) } }
            if (page == Page.HEALTH) {
                item { RuntimeDoctorPanel(hostObservation, busy, controller::refreshHealth) }
                r?.phones?.firstOrNull { it.id == r.selected }?.let { phone ->
                    item { DraftHealthPanel(phone, draftHealth) }
                }
                item { OperationHistoryPanel(history) }
                item { GlassPanel {
                    Text("Quick access", style = MaterialTheme.typography.titleLarge)
                    Text("Add Cyclone Open in Android Quick Settings for each Android user.", color = muted)
                } }
            } else if(r==null){item{Text("Registry unavailable or loading.",color=muted)}}
            else when(page) {
                Page.PHONES -> {
                    item { GlassPanel(selected=r.selected==HOST_ID) {
                        Text("PHYSICAL HOST",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text(Build.MODEL,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        Text("Android "+Build.VERSION.RELEASE+" · "+HostRuntime.status().detail,color=muted)
                        if(r.selected!=HOST_ID) OutlinedButton(enabled=!busy,onClick={controller.select(HOST_ID)}){Text("Select host")}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={returnHome()}){Text("Return home")}
                            OutlinedButton(onClick={nativeSettings()}){Text("Users")}
                        }
                    } }
                    item {
                        Button(
                            enabled = !busy && r.images.isNotEmpty() && catalog != null,
                            onClick = {
                                controller.clearDraftReview()
                                name = "New phone"
                                imageId = r.images.first().id
                                imageQuery = ""
                                referenceId = ""
                                memory = 3072
                                runtime = RuntimeKind.LOCAL_VM
                                dialog = DialogKind.CREATE
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("New phone draft") }
                        if (r.images.isEmpty()) Text("Import an image reference in Images first.", color = muted)
                        if (catalogState == CatalogState.Unavailable) {
                            Text("Reference catalog unavailable. Saved drafts are still available.", color = muted)
                            TextButton(enabled = !busy, onClick = controller::refresh) { Text("Retry catalog") }
                        }
                    }
                    items(r.phones,key={it.id}){ p ->
                        PhoneProfileCard(
                            p, r.images.first { it.id == p.imageId }.androidVersion, r.selected == p.id, busy,
                            onSelect = { controller.select(p.id) },
                            onRename = { target = p.id; name = p.name; dialog = DialogKind.RENAME },
                            onRemove = { target = p.id; dialog = DialogKind.REMOVE_PHONE },
                            referenceLabel = p.reference?.let { key ->
                                catalog?.resolve(key)?.let { "${it.label} · Virtual reference" }
                                    ?: "Saved virtual reference unavailable"
                            },
                            onBackup = { controller.saveBackup(p.id) }
                        )
                    }
                    if (backups.isNotEmpty()) {
                        item { Text("Draft settings backups", style = MaterialTheme.typography.titleLarge) }
                        items(backups, key = { "backup-${it.first}" }) { (id, backup) ->
                            GlassPanel {
                                Text(backup.name, style = MaterialTheme.typography.titleMedium)
                                Text("Android ${backup.androidVersion} · ${backup.memoryMiB} MiB target", color = muted)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy && catalog != null, onClick = {
                                        controller.clearDraftReview()
                                        target = id
                                        referenceId = backup.reference?.takeIf { catalog?.resolve(it) != null }?.id.orEmpty()
                                        dialog = DialogKind.RESTORE
                                    }) { Text("Restore settings") }
                                    TextButton(enabled = !busy, onClick = {
                                        target = id
                                        dialog = DialogKind.REMOVE_BACKUP
                                    }) { Text("Remove backup") }
                                }
                            }
                        }
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(enabled = !busy, onClick = { controller.recheckImage(im.id) }) { Text("Verify again") }
                            TextButton(enabled = !busy, onClick = { restoreTarget = im.id; restorePicker.launch(arrayOf("*/*")) }) { Text("Restore access") }
                            TextButton(enabled=!busy,onClick={target=im.id;dialog=DialogKind.REMOVE_IMAGE}){Text("Remove reference")}
                        }
                    }}
                }
                Page.HEALTH -> Unit
            }
            if(busy)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("Working…",color=muted)}
        }
    }
    if (auroraOpen) {
        AuroraSheet(
            registry, busy,
            dismiss = { auroraOpen = false },
            openAndroidHome = { auroraOpen = false; returnHome() },
            selectDraft = { id -> auroraOpen = false; controller.select(id) },
            interrupted = history?.lastOrNull()?.status == RunStatus.INTERRUPTED
        )
    }
    val d=dialog
    val r=registry
    if(d!=null&&r!=null) AlertDialog(
        onDismissRequest={if(!busy)closeDialog()},
        title={Text(when(d){DialogKind.CREATE->if (draftReview == null) "Create phone draft" else "Review phone draft";DialogKind.RESTORE->"Restore draft settings";DialogKind.REMOVE_BACKUP->"Remove backup?";DialogKind.RENAME->"Rename phone";DialogKind.REMOVE_PHONE->"Remove phone draft?";DialogKind.REMOVE_IMAGE->"Remove image reference?";DialogKind.IMPORT->"Verify image"})},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().verticalScroll(rememberScrollState())){
            if (d == DialogKind.RENAME || d == DialogKind.IMPORT || (d == DialogKind.CREATE && draftReview == null)) {
                OutlinedTextField(value=name,onValueChange={name=it},label={Text("Name")},singleLine=true,enabled=!busy)
            }
            when(d) {
                DialogKind.CREATE -> {
                    val approved = draftReview
                    if (approved != null) {
                        DraftReviewPanel(approved)
                    } else {
                        ReferenceChoices(catalog?.references.orEmpty(), reference?.key, !busy) { selected ->
                            referenceId = selected.key.id
                            memory = selected.suggested.memoryMiB
                        }
                        DraftImagePicker(r.images, imageQuery, imageId, !busy, { imageQuery = it }, { imageId = it })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { draftMemoryOptions.forEach{m->FilterChip(selected=memory==m,onClick={memory=m},label={Text((m/1024).toString()+" GiB")},enabled=!busy)} }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(RuntimeKind.LOCAL_VM,RuntimeKind.REMOTE_VM).forEach{k->FilterChip(selected=runtime==k,onClick={runtime=k},label={Text(if(k==RuntimeKind.LOCAL_VM)"Local VM" else "Remote VM")},enabled=!busy)} }
                    }
                }
                DialogKind.RESTORE -> {
                    Text("Restoring creates a new draft with empty profile storage.", color = muted)
                    val approved = draftReview
                    if (approved != null) DraftReviewPanel(approved) else {
                        ReferenceChoices(catalog?.references.orEmpty(), reference?.key, !busy) { referenceId = it.key.id }
                    }
                }
                DialogKind.REMOVE_BACKUP -> Text("Remove this saved settings copy? Your phone drafts remain available.")
                DialogKind.IMPORT -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (13..16).forEach{v->FilterChip(selected=version==v,onClick={version=v},label={Text(v.toString())},enabled=!busy)} }
                    OutlinedTextField(value=digest,onValueChange={digest=it},label={Text("Expected SHA-256")},enabled=!busy)
                    Text("Use an independently trusted checksum. Large files may take time; file is never executed.",color=muted)
                }
                DialogKind.REMOVE_PHONE -> Text("Removes this configuration and retains its profile files.")
                DialogKind.REMOVE_IMAGE -> Text("Remove the reference, not the source document. Referenced images cannot be removed.")
                DialogKind.RENAME -> Unit
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }},
        dismissButton={TextButton(enabled=!busy,onClick={
            if ((d == DialogKind.CREATE || d == DialogKind.RESTORE) && draftReview != null) controller.clearDraftReview() else closeDialog()
        }){Text(if ((d == DialogKind.CREATE || d == DialogKind.RESTORE) && draftReview != null) "Edit" else "Cancel")}},
        confirmButton={Button(enabled=!busy && when (d) {
            DialogKind.CREATE -> draftReview != null || (reference != null && r.images.any { it.id == imageId })
            DialogKind.RESTORE -> draftReview != null || reference != null
            else -> true
        },onClick={
            when(d){
                DialogKind.CREATE -> if (draftReview != null) controller.publishDraft() else reference?.let {
                    controller.reviewDraft(DraftRequest(name, it.key, imageId, runtime, memory))
                }
                DialogKind.RESTORE -> if (draftReview != null) controller.publishDraft(restore = true) else reference?.let {
                    controller.reviewRestore(target, it.key)
                }
                DialogKind.REMOVE_BACKUP -> controller.deleteBackup(target)
                DialogKind.RENAME -> controller.rename(target, name)
                DialogKind.REMOVE_PHONE -> controller.removePhone(target)
                DialogKind.REMOVE_IMAGE -> controller.removeImage(target)
                DialogKind.IMPORT -> uri?.let { controller.importImage(it, name, version, digest) }
            }
        }){Text(when (d) {
            DialogKind.CREATE -> if (draftReview == null) "Review draft" else "Save draft"
            DialogKind.RESTORE -> if (draftReview == null) "Review restore" else "Restore as new draft"
            DialogKind.IMPORT -> "Verify & add"
            DialogKind.REMOVE_IMAGE, DialogKind.REMOVE_PHONE, DialogKind.REMOVE_BACKUP -> "Remove"
            DialogKind.RENAME -> "Save"
        })}}
    )
}
