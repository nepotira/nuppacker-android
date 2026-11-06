package com.nepo.nuppacker

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PackerViewModel : ViewModel() {

    var folder by mutableStateOf("")
        private set
    var updateFolder by mutableStateOf("")
        private set
    var dlcFolder by mutableStateOf("")
        private set
    var outputPath by mutableStateOf("")
        private set
    var titleId by mutableStateOf("")
        private set
    var titleName by mutableStateOf("")
        private set
    var version by mutableStateOf("1.0.0")
        private set
    var updateVersion by mutableStateOf("1.0.1")
        private set
    var displayVersion by mutableStateOf("1.0.0")
        private set
    var iconPath by mutableStateOf("")
        private set
    // Standard recommended compression level for NUP in emulators (level 3)
    var zstdLevel by mutableIntStateOf(3)
        private set
    var locTitles by mutableStateOf("")
        private set

    var isPacking by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    // Storage calculation state
    var totalDumpBytes by mutableLongStateOf(0L)
        private set
    var freeSpaceBytes by mutableLongStateOf(0L)
        private set
    var estimatedNupBytes by mutableLongStateOf(0L)
        private set
    var isStorageSufficient by mutableStateOf<Boolean?>(null)
        private set
    var isCalculatingStorage by mutableStateOf(false)
        private set
    var ignoreStorageWarning by mutableStateOf(false)
        private set

    fun setIgnoreStorageWarning(ignore: Boolean) {
        ignoreStorageWarning = ignore
    }

    val logs = mutableStateListOf<String>()
    val discoveredPackages = mutableStateListOf<String>()

    fun updateFolderInput(path: String, context: Context) {
        folder = path.trim()
        if (folder.isNotEmpty()) {
            val file = File(folder)
            if (outputPath.isEmpty()) {
                val isSinglePkg = File(file, "exefs").isDirectory || File(file, "romfs").isDirectory
                outputPath = if (isSinglePkg) {
                    "${folder.removeSuffix("/")}.nup"
                } else {
                    File(file, "${file.name}.nup").absolutePath
                }
            }
            if (titleId.isEmpty()) {
                autoDetectTitleId(context)
            }
            inspectPackages(context)
            updateStorageEstimate()
        }
    }

    fun updateOutputDirectory(directoryPath: String) {
        val dir = directoryPath.trim().removeSuffix("/")
        val currentFileName = if (outputPath.isNotEmpty()) {
            File(outputPath).name.ifEmpty { "game.nup" }
        } else {
            "game.nup"
        }
        outputPath = "$dir/$currentFileName"
        updateStorageEstimate()
    }

    fun updateUpdateFolder(path: String) {
        updateFolder = path.trim()
        updateStorageEstimate()
    }

    fun updateDlcFolder(path: String) {
        dlcFolder = path.trim()
        updateStorageEstimate()
    }

    fun updateOutputPath(path: String) {
        outputPath = path.trim()
        updateStorageEstimate()
    }

    fun updateTitleId(tid: String) {
        titleId = tid.trim().removePrefix("0x").removePrefix("0X")
    }

    fun updateTitleName(title: String) {
        titleName = title
    }

    fun updateVersion(v: String) {
        version = v.trim()
    }

    fun updateUpdateVersion(v: String) {
        updateVersion = v.trim()
    }

    fun updateDisplayVersion(v: String) {
        displayVersion = v.trim()
    }

    fun updateIconPath(p: String) {
        iconPath = p.trim()
    }

    fun updateZstdLevel(level: Int) {
        zstdLevel = level.coerceIn(0, 22)
        updateStorageEstimate()
    }

    fun updateLocTitles(titles: String) {
        locTitles = titles
    }

    private var storageJob: Job? = null

    fun updateStorageEstimate() {
        if (folder.isEmpty()) {
            totalDumpBytes = 0L
            freeSpaceBytes = 0L
            estimatedNupBytes = 0L
            isStorageSufficient = null
            isCalculatingStorage = false
            return
        }
        storageJob?.cancel()
        storageJob = viewModelScope.launch(Dispatchers.IO) {
            isCalculatingStorage = true
            try {
                val dumpDir = File(folder)
                if (!dumpDir.exists()) {
                    withContext(Dispatchers.Main) {
                        totalDumpBytes = 0L
                        isStorageSufficient = null
                        isCalculatingStorage = false
                    }
                    return@launch
                }
                var total = calculateDirSize(dumpDir)

                if (updateFolder.isNotEmpty()) {
                    val uf = File(updateFolder)
                    if (uf.exists()) total += calculateDirSize(uf)
                }
                if (dlcFolder.isNotEmpty()) {
                    val df = File(dlcFolder)
                    if (df.exists()) total += calculateDirSize(df)
                }

                // Check free space on the destination drive
                val targetDir = if (outputPath.isNotEmpty()) {
                    val p = File(outputPath).parentFile
                    if (p != null && p.exists()) p else Environment.getExternalStorageDirectory()
                } else {
                    Environment.getExternalStorageDirectory()
                }

                val stat = try {
                    StatFs(targetDir.path)
                } catch (e: Exception) {
                    StatFs(Environment.getExternalStorageDirectory().path)
                }
                val free = stat.availableBlocksLong * stat.blockSizeLong

                // Switch game romfs/exefs average compression ratio with Zstd
                val ratio = when {
                    zstdLevel == 0 -> 1.00 // Uncompressed raw store
                    zstdLevel <= 3 -> 0.58 // ~58% average for Switch romfs/exefs on zstd level 3
                    zstdLevel <= 8 -> 0.52
                    else -> 0.48
                }
                val estimated = (total * ratio).toLong() + (5L * 1024 * 1024) // +5MB TOC headroom
                val sufficient = free > (estimated + (300L * 1024 * 1024)) // 300MB safe buffer

                withContext(Dispatchers.Main) {
                    totalDumpBytes = total
                    freeSpaceBytes = free
                    estimatedNupBytes = estimated
                    isStorageSufficient = sufficient
                    isCalculatingStorage = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCalculatingStorage = false
                }
            }
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }

    fun autoDetectTitleId(context: Context) {
        if (folder.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val tid = PackerBridge.autoDetectTitleId(context, folder)
            withContext(Dispatchers.Main) {
                if (tid.isNotEmpty()) {
                    titleId = tid.removePrefix("0x").removePrefix("0X")
                    addLog("Auto-detected Title ID: $titleId")
                } else {
                    addLog("Could not auto-detect Title ID from main.npdm")
                }
            }
        }
    }

    fun inspectPackages(context: Context) {
        if (folder.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val summary = PackerBridge.discoverSummary(context, folder)
            withContext(Dispatchers.Main) {
                discoveredPackages.clear()
                for (item in summary) {
                    val kind = item["kind"] ?: "unknown"
                    val v = item["version"] ?: "base"
                    val p = item["path"] ?: ""
                    val line = "Found $kind (v$v): $p"
                    discoveredPackages.add(line)
                    addLog(line)
                }
            }
        }
    }

    fun addLog(msg: String) {
        logs.add(msg)
    }

    fun clearLogs() {
        logs.clear()
    }

    fun cancelBuild(context: Context) {
        if (!isPacking) return
        addLog("Cancelling NUP build...")
        PackerBridge.cancelActiveBuild()
        PackerForegroundService.stopOrCancel(context)
        isPacking = false
        statusMessage = "Build cancelled by user."
    }

    fun startBuild(context: Context) {
        if (folder.isEmpty() || outputPath.isEmpty() || titleId.isEmpty()) {
            statusMessage = "Input folder, output path, and Title ID are required."
            return
        }

        val locList = mutableListOf<List<String>>()
        locTitles.lines().forEach { line ->
            if (line.contains("=")) {
                val parts = line.split("=", limit = 2)
                locList.add(listOf(parts[0].trim(), parts[1].trim()))
            }
        }

        val config = mutableMapOf<String, Any?>(
            "folder" to folder,
            "update" to updateFolder.ifEmpty { null },
            "dlc" to dlcFolder.ifEmpty { null },
            "output" to outputPath,
            "title_id" to titleId,
            "title" to titleName,
            "version" to version,
            "update_version" to updateVersion,
            "display_version" to displayVersion,
            "icon" to iconPath.ifEmpty { null },
            "zstd" to zstdLevel,
            "loc_titles" to locList
        )

        val jsonPayload = JSONObject(config).toString()
        val displayName = titleName.ifEmpty { File(outputPath).name }

        isPacking = true
        progress = 0f
        statusMessage = "Packing in progress..."
        addLog("=== Starting NUP Build (High Performance Engine) ===")
        addLog("Target output: $outputPath")
        addLog("Title ID: $titleId | zstd level: $zstdLevel")

        // Start Foreground Service with persistent notification
        PackerForegroundService.startService(context, jsonPayload, displayName)

        viewModelScope.launch(Dispatchers.IO) {
            val callback = object : PackerCallback {
                override fun onLog(message: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog(message)
                    }
                }

                override fun onProgress(ratio: Float) {
                    viewModelScope.launch(Dispatchers.Main) {
                        progress = ratio.coerceIn(0f, 1f)
                        val percent = (progress * 100).toInt()
                        // Update Foreground notification
                        val updateIntent = Intent(context, PackerForegroundService::class.java).apply {
                            action = PackerForegroundService.ACTION_UPDATE_PROGRESS
                            putExtra(PackerForegroundService.EXTRA_TITLE_NAME, displayName)
                            putExtra(PackerForegroundService.EXTRA_PROGRESS, percent)
                            putExtra(PackerForegroundService.EXTRA_STATUS, "Packing files ($percent%)...")
                        }
                        context.startService(updateIntent)
                    }
                }

                override fun onSuccess() {
                    viewModelScope.launch(Dispatchers.Main) {
                        isPacking = false
                        progress = 1f
                        statusMessage = "NUP Build Complete!"
                        addLog("Build completed successfully: $outputPath")
                        PackerForegroundService.stopOrCancel(context)
                    }
                }

                override fun onCancelled() {
                    viewModelScope.launch(Dispatchers.Main) {
                        isPacking = false
                        statusMessage = "Build cancelled."
                        addLog("Operation aborted by user. Temporary files cleaned.")
                        PackerForegroundService.stopOrCancel(context)
                    }
                }

                override fun onError(error: String, traceback: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        isPacking = false
                        statusMessage = "Error: $error"
                        addLog("ERROR: $error")
                        addLog(traceback)
                        PackerForegroundService.stopOrCancel(context)
                    }
                }

                override fun isCancelled(): Boolean {
                    return PackerBridge.isCancellationRequested
                }
            }

            try {
                PackerBridge.executePacker(context, config, callback)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPacking = false
                    statusMessage = "Build failed: ${e.message}"
                    addLog("Exception: ${e.message}")
                    PackerForegroundService.stopOrCancel(context)
                }
            }
        }
    }
}
