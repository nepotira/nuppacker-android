package com.nepo.nuppacker

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

interface PackerCallback {
    fun onLog(message: String)
    fun onProgress(ratio: Float)
    fun onSuccess()
    fun onError(error: String, traceback: String)
}

object PackerBridge {

    fun initPython(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    fun autoDetectTitleId(context: Context, folderPath: String): String {
        return try {
            initPython(context)
            val module = Python.getInstance().getModule("nuppacker")
            val result = module.callAttr("auto_detect_title_id", folderPath)
            result.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun discoverSummary(context: Context, folderPath: String): List<Map<String, Any?>> {
        return try {
            initPython(context)
            val module = Python.getInstance().getModule("nuppacker")
            val pyList = module.callAttr("discover_summary", folderPath).asList()
            pyList.mapNotNull { item ->
                try {
                    val map = item.asMap()
                    map.mapKeys { it.key.toString() }.mapValues { it.value?.toString() }
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun executePacker(
        context: Context,
        config: Map<String, Any?>,
        callback: PackerCallback
    ) {
        initPython(context)
        val module = Python.getInstance().getModule("nuppacker")
        // Serialize to JSON string to prevent Java/Python proxy mismatch (e.g. LinkedHashMap subscript error)
        val jsonPayload = JSONObject(config).toString()
        module.callAttr("run_packer_bridge", jsonPayload, callback)
    }
}
