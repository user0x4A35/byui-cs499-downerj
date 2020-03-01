package io.github.ascenderx.mobilescript.models.scripting

import android.os.Handler
import com.eclipsesource.v8.*
import java.util.concurrent.ConcurrentLinkedQueue

class ScriptEngine private constructor(private val handler: Handler) {
    companion object {
        const val STATUS_ERROR = -1
        const val STATUS_RESULT = 0
        const val STATUS_PRINT = 1
        const val STATUS_PRINT_LINE = 2
        const val STATUS_PROMPT = 3
        const val STATUS_CLEAR = 4
        const val STATUS_RESTART = 5
        const val STATUS_SCRIPT_RUN = 6
        const val STATUS_SCRIPT_END = 7
        const val STATUS_INTERRUPT = 8

        private var instance: ScriptEngine? = null

        fun getInstance(handler: Handler): ScriptEngine {
            return if (instance != null) {
                instance as ScriptEngine
            } else {
                instance = ScriptEngine(handler)
                instance as ScriptEngine
            }
        }
    }

    private var runnable: ScriptRunnable? = ScriptRunnable(this)
    private var thread: Thread? = Thread(runnable)
    @Volatile private var userInput: String? = null
    private var busy: Boolean = false
    val commandHistory: MutableList<String> = mutableListOf()

    fun addSource(source: String) {
        runnable?.sources?.add(source)
    }

    fun addSources(sources: Iterable<String>) {
        for (source in sources) {
            runnable?.sources?.add(source)
        }
    }

    fun start() = thread?.start()

    private fun deleteThread() {
        runnable?.running = false
        thread = null
        runnable = null
    }

    fun interrupt() {
        if (!busy) {
            return
        }

        runnable?.runtime?.terminateExecution()
        sendMessage(STATUS_INTERRUPT, null)
    }

    fun restart(source: String?) {
        // TODO: Kill current runnable and reset to new instance.
        interrupt()
        deleteThread()

        runnable = ScriptRunnable(this)
        thread = Thread(runnable)
        runnable?.sources?.add(source)
        thread?.start()
    }

    fun evaluate(command: String): Int {
        val historyIndex = commandHistory.size
        commandHistory.add(command)
        runnable?.commands?.add(command)
        return historyIndex
    }

    fun returnPrompt(value: String) {
        userInput = value
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
                    engine.sendMessage(STATUS_RESULT, result)
                } else {
                    engine.sendMessage(STATUS_SCRIPT_END, null)
                }
            } catch (exception: V8RuntimeException) {
                val error: String = exception.message ?: ""
                engine.sendMessage(STATUS_ERROR, error)
            }
        }

        private fun loop() {
            running = true

            // Evaluate all sources before starting.
            for (source in sources) {
                executeCommand(source, true)
            }

            while (running) {
                // TODO: Implement full-stop on thread deletion.
                val command: String? = commands.poll()
                if (command != null) {
                    engine.busy = true
                    executeCommand(command, false)
                } else {
                    engine.busy = false
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
                engine.sendMessage(STATUS_PRINT, output)
            }
        }

        class PrintLineCallback(private val engine: ScriptEngine) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val output: String = if ((parameters == null) || (parameters.length() == 0)) {
                    "\n"
                } else {
                    "${parameters[0]}"
                }
                engine.sendMessage(STATUS_PRINT_LINE, output)
            }
        }

        class ClearCallback(private val engine: ScriptEngine) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                engine.sendMessage(STATUS_CLEAR, null)
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
                engine.sendMessage(STATUS_PROMPT, prompt)
                while (engine.userInput == null) { /* loop */ }

                return engine.userInput
            }
        }
    }
}