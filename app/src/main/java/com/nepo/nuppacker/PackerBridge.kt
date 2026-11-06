package com.nepo.nuppacker

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

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
        module.callAttr("run_packer_bridge", config, callback)
    }
}
