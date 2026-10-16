package com.clipsaver.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            ClipSaverTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            vm.onShared(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClipSaver") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Update engine") },
                            leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                            enabled = !state.busy,
                            onClick = { menuOpen = false; vm.updateEngine() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Paste a TikTok link, or tap Share → ClipSaver inside TikTok.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                label = { Text("Video link") },
                placeholder = { Text("https://www.tiktok.com/@user/video/…") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.download() }),
                trailingIcon = {
                    IconButton(enabled = !state.busy, onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            ?.let(vm::onUrlChange)
                    }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
                },
            )

            Spacer(Modifier.height(12.dp))

            if (state.busy) {
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            } else {
                Button(onClick = vm::download, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.busy) {
                if (state.progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.status.isNotBlank()) {
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.saved.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Saved this session", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.saved, key = { it.uri.toString() }) { video ->
                        SavedRow(video)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRow(video: SavedVideo) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                video.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = {
                val open = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(video.uri, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(open) }
            }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
            IconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND)
                    .setType("video/mp4")
                    .putExtra(Intent.EXTRA_STREAM, video.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(share, null))
            }) { Icon(Icons.Default.Share, contentDescription = "Share") }
        }
    }
}
