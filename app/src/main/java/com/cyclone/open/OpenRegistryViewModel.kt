package com.cyclone.open

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyclone.open.core.ImageReferenceException
import com.cyclone.open.core.ImageReferences
import com.cyclone.open.core.InsufficientProfileStorageException
import com.cyclone.open.core.PhoneVolumeRepository
import com.cyclone.open.core.RegistryReadException
import com.cyclone.open.core.RegistryStore
import com.cyclone.open.core.RegistryWriteException
import com.cyclone.open.core.Rules
import com.cyclone.open.core.RuntimeKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RegistryOperationState(
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val completedChanges: Long = 0
)

internal class OpenRegistryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RegistryStore.get(application)
    private val volumes = PhoneVolumeRepository(application, store)
    private val images = ImageReferences(store, application.contentResolver)
    private val operationState = MutableStateFlow(RegistryOperationState())
    val registry = store.committed
    val operation = operationState.asStateFlow()

    init { refresh() }

    fun refresh() = submit(closeDialog = false) {
        volumes.open()
        images.reconcile()
    }

    fun create(name: String, imageId: String, runtime: RuntimeKind, memory: Int) = submit {
        volumes.create(name, imageId, runtime, memory)
    }

    fun select(id: String) = submit(closeDialog = false) { store.change { Rules.select(it, id) } }
    fun rename(id: String, name: String) = submit { store.change { Rules.rename(it, id, name) } }
    fun removePhone(id: String) = submit { store.change { Rules.remove(it, id) } }
    fun removeImage(id: String) = submit { images.remove(id) }
    fun importImage(uri: Uri, name: String, version: Int, digest: String) = submit { images.add(uri, name, version, digest) }

    fun recheckImage(id: String) = submit(closeDialog = false, notice = "Image matches its saved checksum.") {
        images.recheck(id)
    }

    fun restoreImageAccess(id: String, uri: Uri) = submit(closeDialog = false, notice = "Image access restored.") {
        images.restoreAccess(id, uri)
    }

    private fun submit(closeDialog: Boolean = true, notice: String? = null, task: suspend () -> Unit) {
        val before = operationState.value
        if (before.busy) return
        operationState.value = before.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
                operationState.value = before.copy(
                    busy = false,
                    error = null,
                    notice = notice,
                    completedChanges = before.completedChanges + if (closeDialog) 1 else 0
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operationState.value = before.copy(busy = false, error = message(error), notice = null)
            }
        }
    }

    private fun message(error: Exception): String = when (error) {
        is InsufficientProfileStorageException, is ImageReferenceException -> error.message.orEmpty()
        is RegistryReadException -> "Saved records could not be read. Reload the registry to retry."
        is RegistryWriteException -> "The change could not be confirmed. Reload the registry before retrying."
        is IllegalArgumentException -> "Invalid configuration. Check the fields and try again."
        else -> "The operation could not be completed."
    }
}
