package com.nepo.nuppacker

interface RustCallback {
    fun onLog(message: String)
    fun onProgress(ratio: Float)
    fun onSuccess(outputPath: String)
    fun onCancelled()
    fun onError(error: String)
}

object RustBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("nuppacker_android")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
        } catch (e: Exception) {
            isLoaded = false
        }
    }

    fun isNativeEngineAvailable(): Boolean = isLoaded

    @JvmStatic
    external fun autoDetectTitleId(folderPath: String): String

    @JvmStatic
    external fun discoverSummary(folderPath: String): String

    @JvmStatic
    external fun startBuild(jsonConfig: String, callback: RustCallback): Int

    @JvmStatic
    external fun cancelBuild()
}
