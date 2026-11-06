package com.nepo.nuppacker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nepo.nuppacker.ui.theme.NUPPackerTheme
import com.nepo.nuppacker.ui.theme.SwitchBlue
import com.nepo.nuppacker.ui.theme.SwitchRed
import com.nepo.nuppacker.ui.theme.TerminalGreen
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NUPPackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NUPPackerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NUPPackerScreen(viewModel: PackerViewModel = viewModel()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasStoragePermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStoragePermission(context)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val realPath = resolvePathFromUri(context, it)
            if (realPath != null) {
                viewModel.updateFolderInput(realPath, context)
            } else {
                viewModel.addLog("Selected SAF URI: $it (tip: for direct speed, you can paste the absolute path)")
            }
        }
    }

    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "NUP",
                            color = SwitchBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            "Packer",
                            color = SwitchRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Android")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Storage Permission Banner
            if (!hasStoragePermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Storage Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Switch game dumps require direct storage access to read multi-GB files without system sandbox limits.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    )
                                }
                            }
                        ) {
                            Text("Grant All Files Access")
                        }
                    }
                }
            }

            // Input Folder Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Input Game Dump Folder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.folder,
                        onValueChange = { viewModel.updateFolderInput(it, context) },
                        label = { Text("Folder path (e.g. /sdcard/Games/Zelda)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Browse folder")
                            }
                        }
                    )
                    if (viewModel.discoveredPackages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detected packages:", style = MaterialTheme.typography.labelMedium)
                        for (pkg in viewModel.discoveredPackages) {
                            Text("• $pkg", style = MaterialTheme.typography.bodySmall, color = SwitchBlue)
                        }
                    }
                }
            }

            // Game Details Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Package Metadata",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.titleId,
                            onValueChange = { viewModel.updateTitleId(it) },
                            label = { Text("Title ID (16 hex)") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.autoDetectTitleId(context) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Detect Title ID")
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.titleName,
                        onValueChange = { viewModel.updateTitleName(it) },
                        label = { Text("Title Name (e.g. Mario Kart World)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.version,
                            onValueChange = { viewModel.updateVersion(it) },
                            label = { Text("Base Version") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = viewModel.updateVersion,
                            onValueChange = { viewModel.updateUpdateVersion(it) },
                            label = { Text("Update Version") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.displayVersion,
                        onValueChange = { viewModel.updateDisplayVersion(it) },
                        label = { Text("Display Version (e.g. 1.0.0)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Compression Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val levelLabel = when (viewModel.zstdLevel) {
                        0 -> "0 (raw / uncompressed)"
                        3 -> "3 (Recommended for emulators)"
                        else -> "${viewModel.zstdLevel}"
                    }
                    Text(
                        "Zstandard Compression Level: $levelLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = viewModel.zstdLevel.toFloat(),
                        onValueChange = { viewModel.updateZstdLevel(it.toInt()) },
                        valueRange = 0f..22f,
                        steps = 21,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Level 3 is the standard NUP format recommendation for emulators (fast streaming & decompression). Level 0 stores uncompressed (raw).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Advanced Options (Expandable)
            OutlinedCard(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Options (DLC, Icon, Localized Titles)", fontWeight = FontWeight.Medium)
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(visible = showAdvanced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.updateFolder,
                            onValueChange = { viewModel.updateUpdateFolder(it) },
                            label = { Text("Extra Updates folder (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = viewModel.dlcFolder,
                            onValueChange = { viewModel.updateDlcFolder(it) },
                            label = { Text("DLC folder (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = viewModel.iconPath,
                            onValueChange = { viewModel.updateIconPath(it) },
                            label = { Text("Icon path (PNG/JPEG, optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = viewModel.locTitles,
                            onValueChange = { viewModel.updateLocTitles(it) },
                            label = { Text("Localized titles (lang=name, one per line)") },
                            placeholder = { Text("pt-br=Mario Kart\nja=マリオカート") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Output Path Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Output .nup File",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.outputPath,
                        onValueChange = { viewModel.updateOutputPath(it) },
                        label = { Text("Destination .nup path") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Build Action Button
            Button(
                onClick = { viewModel.startBuild(context) },
                enabled = !viewModel.isPacking && viewModel.folder.isNotEmpty() && viewModel.titleId.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SwitchBlue)
            ) {
                if (viewModel.isPacking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Packing NUP...", fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Build NUP File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Progress Indicator
            if (viewModel.isPacking || viewModel.progress > 0f) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            viewModel.statusMessage ?: "Processing...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${(viewModel.progress * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = SwitchBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { viewModel.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SwitchBlue
                    )
                }
            }

            // Console Logs Window
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181825))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Console Output",
                            color = TerminalGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        TextButton(
                            onClick = { viewModel.clearLogs() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Clear", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Divider(color = Color(0xFF313244), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))

                    val listState = rememberLazyListState()
                    LaunchedEffect(viewModel.logs.size) {
                        if (viewModel.logs.isNotEmpty()) {
                            listState.animateScrollToItem(viewModel.logs.size - 1)
                        }
                    }

                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(viewModel.logs) { logLine ->
                                Text(
                                    logLine,
                                    color = if (logLine.contains("ERROR", ignoreCase = true)) SwitchRed else Color(0xFFCDD6F4),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun resolvePathFromUri(context: android.content.Context, treeUri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parts = docId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val relativePath = parts[1]
            return if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            } else {
                "/storage/$type/$relativePath"
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return null
}
