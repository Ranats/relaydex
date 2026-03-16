package io.relaydex.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.relaydex.android.model.ApprovalRequest
import io.relaydex.android.model.ConnectionStatus
import io.relaydex.android.model.ConversationMessage
import io.relaydex.android.model.ConversationRole
import io.relaydex.android.model.ModelOption
import io.relaydex.android.model.ThreadSummary
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun RemodexApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsOpen = remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::updatePairingInput)
    }

    if (state.pendingApproval != null) {
        ApprovalDialog(
            request = state.pendingApproval,
            onApprove = { viewModel.respondToApproval(true) },
            onDecline = { viewModel.respondToApproval(false) },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("Close")
                }
            },
            title = { Text("Error") },
            text = { Text(message) },
        )
    }

    if (settingsOpen.value) {
        RuntimeSettingsDialog(
            models = state.availableModels,
            selectedModelId = state.selectedModelId,
            selectedReasoningEffort = state.selectedReasoningEffort,
            isLoading = state.isLoadingRuntimeConfig,
            onDismiss = { settingsOpen.value = false },
            onReload = viewModel::loadRuntimeConfig,
            onSelectModel = viewModel::selectModel,
            onSelectReasoning = viewModel::selectReasoningEffort,
        )
    }

    if (state.selectedThreadId == null) {
        if (state.connectionStatus == ConnectionStatus.CONNECTED) {
            ThreadListScreen(
                state = state,
                onDisconnect = { viewModel.disconnect(clearSavedPairing = false) },
                onForgetPairing = { viewModel.disconnect(clearSavedPairing = true) },
                onRefresh = viewModel::refreshThreads,
                onCreateThread = viewModel::createThread,
                onOpenThread = viewModel::openThread,
                onOpenSettings = { settingsOpen.value = true },
            )
        } else {
            PairingScreen(
                state = state,
                onPairingInputChanged = viewModel::updatePairingInput,
                onScanQr = {
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the Relaydex pairing QR")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
                onConnect = viewModel::connectWithCurrentPairingInput,
                onReconnectSaved = viewModel::reconnectSaved,
            )
        }
    } else {
        ThreadDetailScreen(
            state = state,
            onBack = viewModel::closeThread,
            onRefresh = { state.selectedThreadId?.let(viewModel::openThread) },
            onComposerChanged = viewModel::updateComposerText,
            onSend = viewModel::sendMessage,
        )
    }
}

@Composable
private fun ApprovalDialog(
    request: ApprovalRequest?,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    val activeRequest = request ?: return
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            Button(onClick = onApprove) {
                Text("Approve")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
        },
        title = { Text("Approval Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(activeRequest.method)
                activeRequest.command?.takeIf { it.isNotBlank() }?.let { Text(it) }
                activeRequest.reason?.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingScreen(
    state: RemodexUiState,
    onPairingInputChanged: (String) -> Unit,
    onScanQr: () -> Unit,
    onConnect: () -> Unit,
    onReconnectSaved: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relaydex") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBanner(
                status = state.connectionStatus,
                detail = state.connectionDetail,
                fingerprint = state.secureFingerprint,
            )

            BusyBanner(state = state)

            Text(
                text = "Run `relaydex up` on your Windows PC or Mac, then scan the QR code from Android or paste the pairing payload shown under the QR.",
                style = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = state.pairingInput,
                onValueChange = onPairingInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Pairing payload") },
                placeholder = { Text("{\"v\":2,...}") },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                    Text("Scan QR")
                }
                Button(
                    onClick = onConnect,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Connect")
                }
            }

            if (state.hasSavedPairing) {
                OutlinedButton(
                    onClick = onReconnectSaved,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reconnect Saved Pairing")
                }
            }

            if (state.isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadListScreen(
    state: RemodexUiState,
    onDisconnect: () -> Unit,
    onForgetPairing: () -> Unit,
    onRefresh: () -> Unit,
    onCreateThread: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threads") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                    TextButton(onClick = onForgetPairing) { Text("Forget") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateThread) {
                Text("New")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .safeDrawingPadding()
        ) {
            StatusBanner(
                status = state.connectionStatus,
                detail = state.connectionDetail,
                fingerprint = state.secureFingerprint,
            )

            BusyBanner(state = state)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.threads, key = { it.id }) { thread ->
                    ThreadCard(thread = thread, onOpenThread = onOpenThread)
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(thread: ThreadSummary, onOpenThread: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(thread.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            thread.preview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = thread.projectName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadDetailScreen(
    state: RemodexUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onComposerChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val listState = remember { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val showJumpToLatest by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex < totalItems - 1
            }
        }
    }

    LaunchedEffect(state.selectedThreadId, state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedThreadTitle ?: "Conversation") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
        ) {
            StatusBanner(
                status = state.connectionStatus,
                detail = state.connectionDetail,
                fingerprint = state.secureFingerprint,
            )

            BusyBanner(state = state)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }

                if (showJumpToLatest) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                if (state.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(state.messages.lastIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    ) {
                        Text("Latest")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.composerText,
                onValueChange = onComposerChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                placeholder = { Text("Ask Codex to inspect or edit your local project") },
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSend,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isBusy) "Working..." else "Send")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    val containerColor = when (message.role) {
        ConversationRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ConversationRole.ASSISTANT -> MaterialTheme.colorScheme.surface
        ConversationRole.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }
    val alignment = if (message.role == ConversationRole.USER) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = when (message.role) {
                        ConversationRole.USER -> "You"
                        ConversationRole.ASSISTANT -> if (message.isStreaming) "Codex (streaming)" else "Codex"
                        ConversationRole.SYSTEM -> "Bridge"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = message.text.ifBlank { " " },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(message.createdAtEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BusyBanner(state: RemodexUiState) {
    val label = state.busyLabel ?: if (state.isLoadingRuntimeConfig) "Loading models..." else null
    if (!state.isBusy && !state.isLoadingRuntimeConfig) {
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            Text(
                text = label ?: "Working...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RuntimeSettingsDialog(
    models: List<ModelOption>,
    selectedModelId: String?,
    selectedReasoningEffort: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onSelectModel: (String?) -> Unit,
    onSelectReasoning: (String?) -> Unit,
) {
    val selectedModel = models.firstOrNull { it.id == selectedModelId || it.model == selectedModelId }
        ?: models.firstOrNull { it.isDefault }
        ?: models.firstOrNull()
    val supportedEfforts = selectedModel?.supportedReasoningEfforts.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onReload) { Text("Reload") }
        },
        title = { Text("Runtime Settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        text = if (isLoading) "Loading models..." else "Choose the model and reasoning effort used for new turns.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text("Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    RuntimeOptionRow(
                        title = "Auto",
                        subtitle = selectedModel?.displayName?.takeIf { it.isNotBlank() }?.let { "Default: $it" },
                        selected = selectedModelId == null,
                        onClick = { onSelectModel(null) },
                    )
                }
                items(models, key = { it.stableIdentifier }) { model ->
                    RuntimeOptionRow(
                        title = model.displayName,
                        subtitle = model.description.ifBlank { model.model },
                        selected = selectedModelId == model.stableIdentifier || selectedModelId == model.model,
                        onClick = { onSelectModel(model.stableIdentifier) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Reasoning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    RuntimeOptionRow(
                        title = "Auto",
                        subtitle = selectedModel?.defaultReasoningEffort?.let { "Default: $it" },
                        selected = selectedReasoningEffort == null,
                        onClick = { onSelectReasoning(null) },
                    )
                }
                items(supportedEfforts, key = { it.reasoningEffort }) { effort ->
                    RuntimeOptionRow(
                        title = effort.reasoningEffort,
                        subtitle = effort.description,
                        selected = selectedReasoningEffort == effort.reasoningEffort,
                        onClick = { onSelectReasoning(effort.reasoningEffort) },
                    )
                }
            }
        },
    )
}

@Composable
private fun RuntimeOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    status: ConnectionStatus,
    detail: String?,
    fingerprint: String?,
) {
    val bannerColor = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF1F6B54)
        ConnectionStatus.CONNECTING, ConnectionStatus.HANDSHAKING -> Color(0xFF9A6A17)
        ConnectionStatus.RECONNECT_REQUIRED, ConnectionStatus.UPDATE_REQUIRED -> Color(0xFF9B2C2C)
        ConnectionStatus.DISCONNECTED -> Color(0xFF5F5A52)
    }

    Surface(
        color = bannerColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = status.name.replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            fingerprint?.takeIf { it.isNotBlank() }?.let {
                Text(text = "Host fingerprint: $it", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
