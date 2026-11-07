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
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class PackerViewModel : ViewModel() {

    var folder by mutableStateOf("")
        private set
    var updateFolder by mutableStateOf("")
        private set
    var dlcFolder by mutableStateOf("")
        private set
    var outputDir by mutableStateOf("")
        private set
    var outputFileName by mutableStateOf("")
        private set
    var outputPath by mutableStateOf("")
        private set
    var isFileNameCustomized by mutableStateOf(false)
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

    fun updateIgnoreStorageWarning(ignore: Boolean) {
        ignoreStorageWarning = ignore
    }

    val logs = mutableStateListOf<String>()
    val discoveredPackages = mutableStateListOf<String>()

    private var cachedFolder: String = ""
    private var cachedUpdate: String = ""
    private var cachedDlc: String = ""
    private var cachedDumpSize: Long = 0L

    private var storageJob: Job? = null

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun updateOutputPathCombined(triggerStorageEstimate: Boolean = true) {
        val cleanName = when {
            outputFileName.isEmpty() -> "game.nup"
            outputFileName.endsWith(".nup", ignoreCase = true) -> outputFileName
            else -> "$outputFileName.nup"
        }
        outputPath = if (outputDir.isNotEmpty()) {
            "${outputDir.removeSuffix("/")}/$cleanName"
        } else {
            cleanName
        }
        if (triggerStorageEstimate) {
            updateStorageEstimate(forceRefreshDir = false)
        }
    }

    fun updateFolderInput(path: String, context: Context) {
        val trimmed = path.trim()
        if (trimmed == folder) return
        folder = trimmed
        if (folder.isNotEmpty()) {
            val file = File(folder)
            if (outputDir.isEmpty()) {
                outputDir = file.parent ?: folder
            }
            if (!isFileNameCustomized) {
                outputFileName = if (titleName.isNotBlank()) {
                    "${sanitizeFileName(titleName)}.nup"
                } else {
                    "${file.name}.nup"
                }
            }
            updateOutputPathCombined(triggerStorageEstimate = false)

            if (RustBridge.isNativeEngineAvailable()) {
                // Unified native scan extracts Title ID, packages, and storage in a single pass
                updateStorageEstimate(forceRefreshDir = true)
            } else {
                if (titleId.isEmpty()) {
                    autoDetectTitleId(context)
                }
                inspectPackages(context)
                updateStorageEstimate(forceRefreshDir = true)
            }
        }
    }

    fun updateOutputDirectory(directoryPath: String) {
        val dir = directoryPath.trim().removeSuffix("/")
        outputDir = dir
        updateOutputPathCombined()
    }

    fun updateOutputFileName(name: String) {
        isFileNameCustomized = true
        outputFileName = name.trim()
        updateOutputPathCombined()
    }

    fun updateOutputPath(path: String) {
        val trimmed = path.trim()
        val f = File(trimmed)
        if (f.parent != null) {
            outputDir = f.parent ?: ""
            outputFileName = f.name
            isFileNameCustomized = true
        } else {
            outputFileName = trimmed
            isFileNameCustomized = true
        }
        outputPath = trimmed
        updateStorageEstimate(forceRefreshDir = false)
    }

    fun updateUpdateFolder(path: String) {
        updateFolder = path.trim()
        updateStorageEstimate(forceRefreshDir = true)
    }

    fun updateDlcFolder(path: String) {
        dlcFolder = path.trim()
        updateStorageEstimate(forceRefreshDir = true)
    }

    fun updateTitleId(tid: String) {
        titleId = tid.trim().removePrefix("0x").removePrefix("0X")
    }

    fun updateTitleName(title: String) {
        titleName = title
        if (!isFileNameCustomized && title.isNotBlank()) {
            outputFileName = "${sanitizeFileName(title)}.nup"
            updateOutputPathCombined()
        }
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
        updateStorageEstimate(forceRefreshDir = false)
    }

    fun updateLocTitles(titles: String) {
        locTitles = titles
    }

    fun recalculateStorageManually() {
        if (folder.isEmpty()) return
        cachedDumpSize = 0L
        updateStorageEstimate(forceRefreshDir = true)
    }

    fun refreshFreeSpace() {
        val targetDir = if (outputPath.isNotEmpty()) {
            val p = File(outputPath).parentFile
            if (p != null && p.exists()) p else Environment.getExternalStorageDirectory()
        } else if (outputDir.isNotEmpty()) {
            val p = File(outputDir)
            if (p.exists()) p else Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStorageDirectory()
        }

        val stat = try {
            StatFs(targetDir.path)
        } catch (e: Exception) {
            StatFs(Environment.getExternalStorageDirectory().path)
        }
        freeSpaceBytes = stat.availableBlocksLong * stat.blockSizeLong
    }

    private fun recalculateRatioAndSufficiency(total: Long) {
        val ratio = when {
            zstdLevel == 0 -> 1.00 // Uncompressed raw store
            zstdLevel <= 3 -> 0.58 // ~58% average for Switch romfs/exefs on zstd level 3
            zstdLevel <= 8 -> 0.52
            else -> 0.48
        }
        val estimated = (total * ratio).toLong() + (5L * 1024 * 1024) // +5MB TOC headroom
        totalDumpBytes = total
        estimatedNupBytes = estimated
        isStorageSufficient = freeSpaceBytes > (estimated + (300L * 1024 * 1024)) // 300MB safe buffer
    }

    fun updateStorageEstimate(forceRefreshDir: Boolean = false) {
        if (folder.isEmpty()) {
            totalDumpBytes = 0L
            freeSpaceBytes = 0L
            estimatedNupBytes = 0L
            isStorageSufficient = null
            isCalculatingStorage = false
            return
        }

        // If folder did not change and size is cached, calculate ratio and space INSTANTLY
        val isSameFolder = folder == cachedFolder && updateFolder == cachedUpdate && dlcFolder == cachedDlc
        if (!forceRefreshDir && isSameFolder && cachedDumpSize > 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                refreshFreeSpace()
                withContext(Dispatchers.Main) {
                    recalculateRatioAndSufficiency(cachedDumpSize)
                }
            }
            return
        }

        // Otherwise, run unified native Rust analysis (or fallback to Java NIO)
        storageJob?.cancel()
        storageJob = viewModelScope.launch(Dispatchers.IO) {
            isCalculatingStorage = true
            refreshFreeSpace()
            try {
                if (RustBridge.isNativeEngineAvailable()) {
                    val jsonStr = RustBridge.analyzeFolder(folder)
                    val obj = org.json.JSONObject(jsonStr)
                    var total = obj.optLong("total_bytes", 0L)
                    val tid = obj.optString("title_id", "")
                    
                    val pkgsArr = obj.optJSONArray("packages")
                    val pkgs = mutableListOf<String>()
                    if (pkgsArr != null) {
                        for (i in 0 until pkgsArr.length()) {
                            val pkgObj = pkgsArr.getJSONObject(i)
                            val k = pkgObj.optString("kind", "pkg")
                            val v = pkgObj.optString("version", "0")
                            val p = pkgObj.optString("path", "")
                            pkgs.add("$k v$v: $p")
                        }
                    }

                    if (updateFolder.isNotEmpty() && updateFolder != folder) {
                        try {
                            val uObj = org.json.JSONObject(RustBridge.analyzeFolder(updateFolder))
                            total += uObj.optLong("total_bytes", 0L)
                        } catch (_: Exception) {}
                    }
                    if (dlcFolder.isNotEmpty() && dlcFolder != folder) {
                        try {
                            val dObj = org.json.JSONObject(RustBridge.analyzeFolder(dlcFolder))
                            total += dObj.optLong("total_bytes", 0L)
                        } catch (_: Exception) {}
                    }

                    cachedFolder = folder
                    cachedUpdate = updateFolder
                    cachedDlc = dlcFolder
                    cachedDumpSize = total

                    withContext(Dispatchers.Main) {
                        if (titleId.isEmpty() && tid.isNotEmpty()) {
                            titleId = tid.removePrefix("0x").removePrefix("0X")
                        }
                        if (pkgs.isNotEmpty()) {
                            discoveredPackages.clear()
                            discoveredPackages.addAll(pkgs)
                        }
                        recalculateRatioAndSufficiency(total)
                        isCalculatingStorage = false
                    }
                } else {
                    val dumpDir = File(folder)
                    if (!dumpDir.exists()) {
                        withContext(Dispatchers.Main) {
                            totalDumpBytes = 0L
                            isStorageSufficient = null
                            isCalculatingStorage = false
                        }
                        return@launch
                    }
                    var total = fastCalculateDirSize(dumpDir)

                    if (updateFolder.isNotEmpty()) {
                        val uf = File(updateFolder)
                        if (uf.exists()) total += fastCalculateDirSize(uf)
                    }
                    if (dlcFolder.isNotEmpty()) {
                        val df = File(dlcFolder)
                        if (df.exists()) total += fastCalculateDirSize(df)
                    }

                    cachedFolder = folder
                    cachedUpdate = updateFolder
                    cachedDlc = dlcFolder
                    cachedDumpSize = total

                    withContext(Dispatchers.Main) {
                        recalculateRatioAndSufficiency(total)
                        isCalculatingStorage = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCalculatingStorage = false
                }
            }
        }
    }

    private fun fastCalculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        try {
            Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = file.fileName?.toString() ?: ""
                    // Exclude any existing .nup or .part files inside the dump directory
                    if (!name.endsWith(".nup", ignoreCase = true) && !name.endsWith(".part", ignoreCase = true)) {
                        total += attrs.size()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            total = legacyCalculateDirSize(dir)
        }
        return total
    }

    private fun legacyCalculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            val name = f.name
            if (name.endsWith(".nup", ignoreCase = true) || name.endsWith(".part", ignoreCase = true)) continue
            size += if (f.isDirectory) legacyCalculateDirSize(f) else f.length()
        }
        return size
    }

    fun autoDetectTitleId(context: Context) {
        if (folder.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val tid = if (RustBridge.isNativeEngineAvailable()) {
                RustBridge.autoDetectTitleId(folder)
            } else {
                PackerBridge.autoDetectTitleId(context, folder)
            }
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
            val summary = if (RustBridge.isNativeEngineAvailable()) {
                val jsonStr = RustBridge.discoverSummary(folder)
                val list = mutableListOf<Map<String, String>>()
                try {
                    val arr = org.json.JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(mapOf(
                            "kind" to obj.optString("kind", "unknown"),
                            "version" to obj.optString("version", "0"),
                            "path" to obj.optString("path", "")
                        ))
                    }
                } catch (e: Exception) {
                    // Fallback
                }
                list
            } else {
                PackerBridge.discoverSummary(context, folder)
            }
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
        if (RustBridge.isNativeEngineAvailable()) {
            RustBridge.cancelBuild()
        }
        PackerBridge.cancelActiveBuild()
        PackerForegroundService.stopOrCancel(context)
        isPacking = false
        statusMessage = "Build cancelled by user."

        val targetPath = outputPath
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val partFile = File("$targetPath.part")
                if (partFile.exists()) {
                    partFile.delete()
                }
            } catch (_: Exception) {}
        }
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
        val isNative = RustBridge.isNativeEngineAvailable()
        addLog(if (isNative) "=== Starting NUP Build (Native Rust Core / ARMv8 Hardware Crypto) ===" else "=== Starting NUP Build (Python Engine) ===")
        addLog("Target output: $outputPath")
        addLog("Title ID: $titleId | zstd level: $zstdLevel")

        // Start Foreground Service with persistent notification
        PackerForegroundService.startService(context, jsonPayload, displayName)

        var lastReportedPercent = -1

        viewModelScope.launch(Dispatchers.IO) {
            if (isNative) {
                val rustCallback = object : RustCallback {
                    override fun onLog(message: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            addLog(message)
                        }
                    }

                    override fun onProgress(ratio: Float) {
                        viewModelScope.launch(Dispatchers.Main) {
                            progress = ratio.coerceIn(0f, 1f)
                            val percent = (progress * 100).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                val updateIntent = Intent(context, PackerForegroundService::class.java).apply {
                                    action = PackerForegroundService.ACTION_UPDATE_PROGRESS
                                    putExtra(PackerForegroundService.EXTRA_TITLE_NAME, displayName)
                                    putExtra(PackerForegroundService.EXTRA_PROGRESS, percent)
                                    putExtra(PackerForegroundService.EXTRA_STATUS, "Packing files ($percent%)...")
                                }
                                context.startService(updateIntent)
                            }
                        }
                    }

                    override fun onSuccess(outputPath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            isPacking = false
                            progress = 1f
                            statusMessage = "NUP Build Complete!"
                            addLog("Build completed successfully: $outputPath")
                            PackerForegroundService.stopOrCancel(context)
                            refreshFreeSpace()
                            recalculateRatioAndSufficiency(cachedDumpSize)
                        }
                    }

                    override fun onCancelled() {
                        viewModelScope.launch(Dispatchers.Main) {
                            isPacking = false
                            statusMessage = "Build cancelled."
                            addLog("Operation aborted by user. Temporary files cleaned.")
                            PackerForegroundService.stopOrCancel(context)
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val partFile = File("$outputPath.part")
                                    if (partFile.exists()) {
                                        partFile.delete()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onError(error: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            isPacking = false
                            statusMessage = "Error: $error"
                            addLog("ERROR: $error")
                            PackerForegroundService.stopOrCancel(context)
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val partFile = File("$outputPath.part")
                                    if (partFile.exists()) {
                                        partFile.delete()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                RustBridge.startBuild(jsonPayload, rustCallback)
            } else {
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
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                val updateIntent = Intent(context, PackerForegroundService::class.java).apply {
                                    action = PackerForegroundService.ACTION_UPDATE_PROGRESS
                                    putExtra(PackerForegroundService.EXTRA_TITLE_NAME, displayName)
                                    putExtra(PackerForegroundService.EXTRA_PROGRESS, percent)
                                    putExtra(PackerForegroundService.EXTRA_STATUS, "Packing files ($percent%)...")
                                }
                                context.startService(updateIntent)
                            }
                        }
                    }

                    override fun onSuccess() {
                        viewModelScope.launch(Dispatchers.Main) {
                            isPacking = false
                            progress = 1f
                            statusMessage = "NUP Build Complete!"
                            addLog("Build completed successfully: $outputPath")
                            PackerForegroundService.stopOrCancel(context)
                            refreshFreeSpace()
                            recalculateRatioAndSufficiency(cachedDumpSize)
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
}
