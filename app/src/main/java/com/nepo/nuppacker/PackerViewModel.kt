package com.nepo.nuppacker

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // Standard recommended compression level for NUP in emulators (fast streaming + high ratio)
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
        }
    }

    fun updateUpdateFolder(path: String) {
        updateFolder = path.trim()
    }

    fun updateDlcFolder(path: String) {
        dlcFolder = path.trim()
    }

    fun updateOutputPath(path: String) {
        outputPath = path.trim()
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
    }

    fun updateLocTitles(titles: String) {
        locTitles = titles
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

        isPacking = true
        progress = 0f
        statusMessage = "Packing in progress..."
        addLog("=== Starting NUP Build ===")
        addLog("Target output: $outputPath")
        addLog("Title ID: $titleId | zstd level: $zstdLevel")

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
                    }
                }

                override fun onSuccess() {
                    viewModelScope.launch(Dispatchers.Main) {
                        isPacking = false
                        progress = 1f
                        statusMessage = "NUP Build Complete!"
                        addLog("Build completed successfully: $outputPath")
                    }
                }

                override fun onError(error: String, traceback: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        isPacking = false
                        statusMessage = "Error: $error"
                        addLog("ERROR: $error")
                        addLog(traceback)
                    }
                }
            }

            try {
                PackerBridge.executePacker(context, config, callback)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPacking = false
                    statusMessage = "Build failed: ${e.message}"
                    addLog("Exception: ${e.message}")
                }
            }
        }
    }
}
