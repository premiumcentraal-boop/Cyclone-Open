package com.cyclone.open.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cyclone.open.core.ControllerAction
import com.cyclone.open.core.LifecycleRun
import com.cyclone.open.core.RunReason
import com.cyclone.open.core.RunStatus

@Composable
internal fun OperationHistoryPanel(history: List<LifecycleRun>?) = GlassPanel {
    Text("Recent operations", style = MaterialTheme.typography.titleLarge)
    when {
        history == null -> Text("Operation history is unavailable.", color = OpenGlass.Muted)
        history.isEmpty() -> Text("No operations recorded.", color = OpenGlass.Muted)
        else -> history.takeLast(8).asReversed().forEach { run ->
            Text("${run.action.label()} · ${run.status.label()}")
            val reason = when (run.reason) {
                RunReason.NONE -> null
                RunReason.STORAGE_FULL -> "Available storage was insufficient."
                RunReason.REVIEW_REQUIRED -> "Review the draft again before retrying."
                RunReason.IO_FAILURE -> "The operation's saved state could not be confirmed."
                RunReason.INTERRUPTED -> "The outcome needs verification in the saved drafts."
                RunReason.OPERATION_FAILED -> "The operation could not be completed."
            }
            reason?.let { Text(it, color = OpenGlass.Muted) }
        }
    }
}

private fun ControllerAction.label() = when (this) {
    ControllerAction.CREATE_DRAFT -> "Create draft"
    ControllerAction.RESTORE_DRAFT -> "Restore settings"
    ControllerAction.SAVE_BACKUP -> "Save backup"
    ControllerAction.REMOVE_BACKUP -> "Remove backup"
    ControllerAction.RETURN_HOME -> "Confirm Android host"
}

private fun RunStatus.label() = when (this) {
    RunStatus.STARTED -> "In progress"
    RunStatus.SUCCEEDED -> "Completed"
    RunStatus.FAILED -> "Failed"
    RunStatus.INTERRUPTED -> "Interrupted"
}
