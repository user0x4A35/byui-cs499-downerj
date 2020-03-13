package io.github.ascenderx.mobilescript.models.scripting

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Handler
import com.eclipsesource.v8.*
import io.github.ascenderx.mobilescript.R
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

class ScriptEngine (private val handler: Handler, private val context: Context) {
    companion object {
        const val EVENT_SOURCE_LOAD_ERROR = -2
        const val EVENT_EVALUATE_ERROR = -1
        const val EVENT_INITIALIZED = 0
        const val EVENT_RESULT = 1
        const val EVENT_PRINT = 2
        const val EVENT_PRINT_LINE = 3
        const val EVENT_PROMPT = 4
        const val EVENT_CLEAR = 5
        const val EVENT_RESTART = 6
        const val EVENT_SCRIPT_RUN = 7
        const val EVENT_SCRIPT_END = 8
        const val EVENT_INTERRUPT = 9
        const val EVENT_SHORTCUT_CREATED = 10

        const val STATUS_INTERRUPTED = -1
        const val STATUS_READY = 0
        const val STATUS_BUSY = 1
        const val STATUS_WAITING = 2
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private val assetManager: AssetManager = context.assets
    private var runnable: ScriptRunnable?
    private var thread: Thread?
    @Volatile private var userInput: String? = null
    @Volatile private var status: Int = STATUS_READY
    val commandHistory: MutableList<String> = mutableListOf()
    var currentFileUri: Uri? = null

    init {
        runnable = ScriptRunnable(this)
        thread = Thread(runnable)

        val assetPaths: List<String> = getScriptAssetPaths()
        for (path in assetPaths) {
            loadAssetSource(path)
        }
    }

    private fun getScriptAssetPaths(): List<String> {
        val paths: MutableList<String> = mutableListOf()
        val directory: String = context.getString(R.string.dir_assets_sources)
        val fileNames: Array<String>? = assetManager.list(directory)

        if (fileNames != null) {
            for (fileName in fileNames) {
                paths.add("$directory/$fileName")
            }
        }

        return paths
    }

    private fun loadAssetSource(filePath: String) {
        try {
            val source: String = readAssetSourceFromPath(filePath)
            runnable?.sources?.add(source)
        } catch (ex: IOException) {
            val messagePart: String = context.getString(R.string.message_error_asset_source)
            sendMessage(
                EVENT_SOURCE_LOAD_ERROR,
                "$messagePart \"$filePath\": ${ex.message}"
            )
        }
    }

    fun loadUserSource(fileUri: Uri) {
        try {
            val source: String = readUserSourceFromContentUri(fileUri)
            runnable?.sources?.add(source)
            currentFileUri = fileUri
            sendMessage(EVENT_SCRIPT_RUN, null)
        } catch (ex: Exception) {
            val messagePart: String = context.getString(R.string.message_error_user_source)
            sendMessage(
                EVENT_SOURCE_LOAD_ERROR,
                "$messagePart \"${fileUri.path}\": ${ex.message}"
            )
        }
    }

    private fun readAssetSourceFromPath(path: String): String {
        val stream: InputStream = assetManager.open(path)
        val reader = InputStreamReader(stream)
        val buffer = StringBuffer()
        var ch: Int = reader.read()
        while (ch >= 0) {
            buffer.append(ch.toChar())
            ch = reader.read()
        }
        return buffer.toString()
    }

    private fun readUserSourceFromContentUri(uri: Uri): String {
        val stream: InputStream = contentResolver.openInputStream(uri)
            ?: throw IOException(context.getString(R.string.message_error_content_open))
        val reader = InputStreamReader(stream)
        val buffer = StringBuffer()
        var ch: Int = reader.read()
        while (ch >= 0) {
            buffer.append(ch.toChar())
            ch = reader.read()
        }
        return buffer.toString()
    }

    fun start() = thread?.start()

    fun kill() {
        runnable?.running = false
        runnable?.runtime?.terminateExecution()
    }

    fun interrupt() {
        if (status == STATUS_READY) {
            return
        }

        status = STATUS_INTERRUPTED
        runnable?.commands?.clear()
        runnable?.sources?.clear()
        runnable?.runtime?.terminateExecution()
        // Run an empty command to "clear" any errors from the runtime.
        runnable?.commands?.add("")

        sendMessage(EVENT_INTERRUPT, null)
        status = STATUS_READY
    }

    fun postData(data: String): Boolean {
        when (status) {
            STATUS_READY -> {
                commandHistory.add(data)
                runnable?.commands?.add(data)
                return true
            }
            STATUS_WAITING -> {
                userInput = data
                return true
            }
        }
        return false
    }

    fun sendMessage(what: Int, data: String?) {
        val message = handler.obtainMessage(what, data)
        handler.sendMessage(message)
    }

    private class ScriptRunnable(private val engine: ScriptEngine) : Runnable {
        val commands: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        val sources: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        var running: Boolean = false
        // Cannot init runtime until run() is called in order to preserve Thread-safety for the
        // V8 runtime.
        lateinit var runtime: V8

        private fun executeCommand(command: String, isSource: Boolean) {
            try {
                val result: String = (
                    runtime.executeScript(command) ?: "undefined"
                ).toString()
                if (!isSource) {
                    engine.sendMessage(EVENT_RESULT, result)
                } else {
                    engine.sendMessage(EVENT_SCRIPT_END, null)
                }
            } catch (ex: Exception) {
                val error: String = ex.message ?: ""
                engine.sendMessage(EVENT_EVALUATE_ERROR, error)
            }
        }

        private fun loop() {
            running = true

            // Evaluate all sources before starting.
            engine.status = STATUS_BUSY
            for (source in sources) {
                executeCommand(source, true)
            }
            engine.sendMessage(EVENT_INITIALIZED, null)
            engine.status = STATUS_READY

            while (running) {
                val command: String? = commands.poll()
                if (command != null) {
                    engine.status = STATUS_BUSY
                    executeCommand(command, false)
                } else if (engine.status != STATUS_INTERRUPTED) {
                    engine.status = STATUS_READY
                }
            }
            runtime.release(true)
        }

        override fun run() {
            runtime = V8.createV8Runtime()
            runtime.registerJavaMethod(
                PrintCallback(engine),
                "print"
            )
            runtime.registerJavaMethod(
                PrintLineCallback(engine),
                "println"
            )
            runtime.registerJavaMethod(
                ClearCallback(engine),
                "clear"
            )
            runtime.registerJavaMethod(
                PromptCallback(engine),
                "prompt"
            )
            loop()
        }

        class PrintCallback(private val engine: ScriptEngine) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                if ((parameters == null) || (parameters.length() == 0)) {
                    return
                }
                val output = "${parameters[0]}"
                engine.sendMessage(EVENT_PRINT, output)
            }
        }

        class PrintLineCallback(private val engine: ScriptEngine) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val output: String = if ((parameters == null) || (parameters.length() == 0)) {
                    "\n"
                } else {
                    "${parameters[0]}"
                }
                engine.sendMessage(EVENT_PRINT_LINE, output)
            }
        }

        class ClearCallback(private val engine: ScriptEngine) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                engine.sendMessage(EVENT_CLEAR, null)
            }
        }

        class PromptCallback(private val engine: ScriptEngine) : JavaCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?): Any? {
                val prompt: String = if (
                    parameters != null &&
                    parameters.length() > 0 &&
                    parameters[0] != null
                ) {
                    parameters[0].toString()
                } else {
                    ""
                }

                engine.userInput = null
                engine.sendMessage(EVENT_PROMPT, prompt)
                while (engine.userInput == null) {
                    engine.status = STATUS_WAITING
                }
                engine.status = STATUS_BUSY

                return engine.userInput
            }
        }
    }
}