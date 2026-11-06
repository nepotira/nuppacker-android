package com.nepo.nuppacker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nepo.nuppacker.ui.theme.NUPPackerTheme
import com.nepo.nuppacker.ui.theme.SwitchBlue
import com.nepo.nuppacker.ui.theme.SwitchRed
import com.nepo.nuppacker.ui.theme.TerminalGreen

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
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    // Dismiss focus and keyboard automatically when app is minimized or backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                focusManager.clearFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var hasStoragePermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    var isBatteryOptimized by remember {
        mutableStateOf(!isIgnoringBatteryOptimizations(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStoragePermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission callback */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Input folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val realPath = resolvePathFromUri(context, it)
            if (realPath != null) {
                viewModel.updateFolderInput(realPath, context)
            } else {
                viewModel.addLog("Selected SAF URI: $it (tip: you can also paste direct path)")
            }
        }
    }

    // Output destination folder picker
    val outputFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val realPath = resolvePathFromUri(context, it)
            if (realPath != null) {
                viewModel.updateOutputDirectory(realPath)
                viewModel.addLog("Output folder updated: $realPath")
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = SwitchRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Storage Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Switch game dumps require direct storage access to read multi-GB files without system sandbox limits.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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

            // Battery Optimization Banner (OEM specific)
            if (isBatteryOptimized) {
                val oemName = detectOemName()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = SwitchBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Background Battery Optimization",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "To ensure packing runs seamlessly in the background with the screen turned off (like ZArchiver), disable battery optimization for NUPPacker.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (oemName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Detected System: $oemName. ${getOemTip(oemName)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    requestDisableBatteryOptimization(context)
                                    isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
                                }
                            ) {
                                Text("Disable Optimization")
                            }
                        }
                    }
                }
            }

            // Input Folder Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
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
                        label = { Text("Folder path (e.g. /sdcard/Games/Mario)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

            // Package Metadata Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
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
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.version,
                            onValueChange = { viewModel.updateVersion(it) },
                            label = { Text("Base Version") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        OutlinedTextField(
                            value = viewModel.updateVersion,
                            onValueChange = { viewModel.updateUpdateVersion(it) },
                            label = { Text("Update Version") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.displayVersion,
                        onValueChange = { viewModel.updateDisplayVersion(it) },
                        label = { Text("Display Version (e.g. 1.0.0)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
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
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        OutlinedTextField(
                            value = viewModel.dlcFolder,
                            onValueChange = { viewModel.updateDlcFolder(it) },
                            label = { Text("DLC folder (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        OutlinedTextField(
                            value = viewModel.iconPath,
                            onValueChange = { viewModel.updateIconPath(it) },
                            label = { Text("Icon path (PNG/JPEG, optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        OutlinedTextField(
                            value = viewModel.locTitles,
                            onValueChange = { viewModel.updateLocTitles(it) },
                            label = { Text("Localized titles (lang=name, one per line)") },
                            placeholder = { Text("pt-br=Mario Kart\nja=マリオカート") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Output Path Card (with Directory Browse Button)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        trailingIcon = {
                            IconButton(onClick = { outputFolderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Select destination folder")
                            }
                        }
                    )
                }
            }

            // Storage Estimation & Space Check Card
            if (viewModel.folder.isNotEmpty()) {
                val isInsufficient = viewModel.isStorageSufficient == false
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInsufficient) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    border = if (isInsufficient) BorderStroke(1.dp, SwitchRed) else null
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isInsufficient) Icons.Default.Warning else Icons.Default.Storage,
                                contentDescription = null,
                                tint = if (isInsufficient) SwitchRed else SwitchBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Storage & Space Estimation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isInsufficient) SwitchRed else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (viewModel.isCalculatingStorage) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = SwitchBlue
                                )
                                Text(
                                    "Calculating dump size & checking disk space...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Original Dump Size:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatFileSize(viewModel.totalDumpBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Estimated .NUP (Level ${viewModel.zstdLevel}):",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "~${formatFileSize(viewModel.estimatedNupBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SwitchBlue
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Available Free Space:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatFileSize(viewModel.freeSpaceBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isInsufficient) SwitchRed else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isInsufficient) {
                                Divider(color = SwitchRed.copy(alpha = 0.3f), thickness = 1.dp)
                                Text(
                                    "⚠️ Warning: Your device may run out of storage space! Estimated output (~${formatFileSize(viewModel.estimatedNupBytes)}) plus safe operating buffer exceeds free space (${formatFileSize(viewModel.freeSpaceBytes)}).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwitchRed,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = viewModel.ignoreStorageWarning,
                                        onCheckedChange = { viewModel.updateIgnoreStorageWarning(it) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Ignore storage warning and build anyway",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else if (viewModel.isStorageSufficient == true) {
                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Sufficient free space available on destination drive.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val hasEnoughStorage = viewModel.isStorageSufficient != false || viewModel.ignoreStorageWarning
            val canBuild = !viewModel.isPacking &&
                viewModel.folder.isNotEmpty() &&
                viewModel.titleId.isNotEmpty() &&
                hasEnoughStorage

            // Build Action Button
            Button(
                onClick = { viewModel.startBuild(context) },
                enabled = canBuild,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!hasEnoughStorage && viewModel.folder.isNotEmpty()) MaterialTheme.colorScheme.error else SwitchBlue
                )
            ) {
                if (viewModel.isPacking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Packing NUP...", fontSize = 16.sp)
                } else if (!hasEnoughStorage && viewModel.folder.isNotEmpty()) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insufficient Storage Space", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Build NUP File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Cancel Button (Active when packing)
            if (viewModel.isPacking) {
                OutlinedButton(
                    onClick = { viewModel.cancelBuild(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SwitchRed),
                    border = BorderStroke(1.dp, SwitchRed)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Build")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Build", fontWeight = FontWeight.Bold)
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

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

@SuppressLint("BatteryLife")
private fun requestDisableBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallback)
        }
    }
}

private fun detectOemName(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "HyperOS / MIUI"
        m.contains("samsung") -> "Samsung One UI"
        m.contains("motorola") -> "Motorola Moto UI"
        else -> "Android Puro / AOSP"
    }
}

private fun getOemTip(oem: String): String {
    return when (oem) {
        "HyperOS / MIUI" -> "Em Configurações > Apps > NUPPacker, ative 'Início automático' e defina Economia de Bateria como 'Sem restrições'."
        "Samsung One UI" -> "Em Assistência do aparelho > Bateria, verifique se o app não está em 'Aplicativos suspensos'."
        "Motorola Moto UI" -> "Verifique 'Gerenciamento em segundo plano' nas opções de bateria."
        else -> "Permita atividade irrestrita em segundo plano para o aplicativo."
    }
}

private fun resolvePathFromUri(context: Context, treeUri: Uri): String? {
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.2f %s", value, units[digitGroups])
}
