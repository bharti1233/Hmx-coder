package com.hmx.harness.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmx.harness.data.update.UpdateManager
import com.hmx.harness.ui.chat.MarkdownText
import com.hmx.harness.ui.common.AppLinearProgressIndicator
import java.io.File

@Composable
fun UpdateDialog(
    state: UpdateManager.State,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenSystemInstaller: (File) -> Unit,
    onOpenUnknownSources: () -> Unit,
) {
    val release = when (state) {
        is UpdateManager.State.Available -> state.release
        is UpdateManager.State.Downloading -> state.release
        is UpdateManager.State.DownloadComplete -> state.release
        is UpdateManager.State.Verifying -> state.release
        is UpdateManager.State.ReadyToInstall -> state.release
        is UpdateManager.State.PermissionRequired -> state.release
        is UpdateManager.State.Installing -> state.release
        is UpdateManager.State.InstallCancelled -> state.release
        is UpdateManager.State.Installed -> state.release
        is UpdateManager.State.Failed -> state.release
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = { if (state !is UpdateManager.State.Installing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (state) {
                    is UpdateManager.State.Downloading -> Icons.Outlined.CloudDownload
                    is UpdateManager.State.Failed -> Icons.Outlined.WarningAmber
                    is UpdateManager.State.Installed -> Icons.Outlined.CheckCircle
                    else -> Icons.Outlined.SystemUpdate
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title(state, release.versionName), style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Version ${release.versionName}  ·  build ${release.versionCode}", style = MaterialTheme.typography.labelMedium)
                Text(release.body.ifBlank { "No release notes were provided." }, style = MaterialTheme.typography.bodyMedium)
                when (state) {
                    is UpdateManager.State.Downloading -> {
                        val fraction = (state.percent / 100f).coerceIn(0f, 1f)
                        AppLinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Text("${state.percent}%  ·  ${state.bytes / 1_048_576} / ${state.total / 1_048_576} MB")
                    }
                    is UpdateManager.State.DownloadComplete, is UpdateManager.State.Verifying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Verifying APK checksum, package identity, version and signing certificate.")
                        }
                    }
                    is UpdateManager.State.ReadyToInstall -> VerificationLine("Checksum and HMXHARNESS signing certificate verified.")
                    is UpdateManager.State.PermissionRequired -> Text("Allow HMXHARNESS to install updates in Android settings, then return and tap Install.")
                    is UpdateManager.State.Installing -> Text("Android will show its installation confirmation. Approve it there to continue.")
                    is UpdateManager.State.InstallCancelled -> Text("Installation was cancelled. The verified APK is still available to retry.")
                    is UpdateManager.State.Installed -> Text("Android reported that the update was installed.")
                    is UpdateManager.State.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                if (release.htmlUrl.startsWith("https://github.com/")) {
                    val context = LocalContext.current
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))) } }) {
                        Text("View release on GitHub")
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateManager.State.Available -> Button(onClick = onDownload) { Text("Download") }
                is UpdateManager.State.Downloading -> TextButton(onClick = onCancelDownload) { Text("Cancel download") }
                is UpdateManager.State.ReadyToInstall, is UpdateManager.State.InstallCancelled -> Button(onClick = onInstall) { Text("Install update") }
                is UpdateManager.State.PermissionRequired -> Button(onClick = onOpenUnknownSources) { Text("Open settings") }
                is UpdateManager.State.Failed -> Button(onClick = if (state.stage == UpdateManager.Stage.CHECK) onDismiss else onDownload) { Text("Retry") }
                else -> Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateManager.State.ReadyToInstall -> TextButton(onClick = { onOpenSystemInstaller(state.apk) }) { Text("System installer") }
                is UpdateManager.State.PermissionRequired -> TextButton(onClick = onInstall) { Text("Retry install") }
                is UpdateManager.State.Installing -> Unit
                else -> TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
    )
}

@Composable
private fun VerificationLine(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun title(state: UpdateManager.State, version: String): String = when (state) {
    is UpdateManager.State.Available -> "Update available"
    is UpdateManager.State.Downloading -> "Downloading update"
    is UpdateManager.State.DownloadComplete, is UpdateManager.State.Verifying -> "Verifying update"
    is UpdateManager.State.ReadyToInstall -> "Ready to install"
    is UpdateManager.State.PermissionRequired -> "Permission required"
    is UpdateManager.State.Installing -> "Waiting for Android"
    is UpdateManager.State.InstallCancelled -> "Installation cancelled"
    is UpdateManager.State.Installed -> "Update installed"
    is UpdateManager.State.Failed -> "Update failed"
    else -> "HMXHARNESS $version"
}
