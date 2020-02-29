package io.github.ascenderx.mobilescript.models.scripting

import android.os.Handler
import android.os.Message
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

    private var runnable: ScriptRunnable = ScriptRunnable(handler)
    private var thread: Thread = Thread(runnable)
    val commandHistory: MutableList<String> = mutableListOf()

    fun restart(source: String?) {
        // TODO: Kill current runnable and reset to new instance.
        runnable.running = false
        runnable = ScriptRunnable(handler)
        thread = Thread(runnable)
        runnable.sources.add(source)
        thread.start()
    }

    fun addSource(source: String) {
        runnable.sources.add(source)
    }

    fun addSources(sources: Iterable<String>) {
        for (source in sources) {
            runnable.sources.add(source)
        }
    }

    fun start() = thread.start()

    fun evaluate(command: String): Int {
        val historyIndex = commandHistory.size
        commandHistory.add(command)
        runnable.commands.add(command)
        return historyIndex
    }

    private class ScriptRunnable(private val handler: Handler) : Runnable {
        val commands: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        val sources: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        var running: Boolean = false
        @Volatile var userInput: String? = null
        // Cannot init runtime until run() is called in order to preserve Thread-safety for the
        // V8 runtime.
        lateinit var runtime: V8

        private fun executeCommand(command: String, isSource: Boolean) {
            try {
                val result: String = (
                    runtime.executeScript(command) ?: "undefined"
                ).toString()
                if (!isSource) {
                    sendResultMessage(result)
                } else {
                    sendSourceFinishMessage()
                }
            } catch (exception: V8RuntimeException) {
                val error: String = exception.message ?: ""
                sendErrorMessage(error)
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
                    executeCommand(command, false)
                }
            }
            runtime.release(true)
        }

        fun sendResultMessage(result: String) {
            val message: Message = handler.obtainMessage(
                STATUS_RESULT,
                result
            )
            handler.sendMessage(message)
        }

        fun sendErrorMessage(error: String) {
            val message = handler.obtainMessage(
                STATUS_ERROR,
                error
            )
            handler.sendMessage(message)
        }

        fun sendSourceFinishMessage() {
            val message = handler.obtainMessage(
                STATUS_SCRIPT_END
            )
            handler.sendMessage(message)
        }

        override fun run() {
            runtime = V8.createV8Runtime()
            runtime.registerJavaMethod(
                PrintCallback(handler),
                "print"
            )
            runtime.registerJavaMethod(
                PrintLineCallback(handler),
                "println"
            )
            runtime.registerJavaMethod(
                ClearCallback(handler),
                "clear"
            )
            runtime.registerJavaMethod(
                SleepCallback(),
                "sleep"
            )
            runtime.registerJavaMethod(
                PromptCallback(handler, userInput),
                "prompt"
            )
            loop()
        }

        class PrintCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                if ((parameters == null) || (parameters.length() == 0)) {
                    return
                }
                val output = "${parameters[0]}"
                val message = handler.obtainMessage(STATUS_PRINT, output)
                handler.sendMessage(message)
            }
        }

        class PrintLineCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val output: String = if ((parameters == null) || (parameters.length() == 0)) {
                    "\n"
                } else {
                    "${parameters[0]}"
                }
                val message: Message = handler.obtainMessage(
                    STATUS_PRINT_LINE,
                    output
                )
                handler.sendMessage(message)
            }
        }

        class ClearCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val message: Message = handler.obtainMessage(
                    STATUS_CLEAR
                )
                handler.sendMessage(message)
            }
        }

        class SleepCallback : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                if ((parameters == null) || (parameters.length() == 0)) {
                    return
                }
                val milliseconds: Long = (parameters[0] as Int).toLong()
                if (milliseconds < 0) {
                    return
                }

                Thread.sleep(milliseconds)
            }
        }

        class PromptCallback(private val handler: Handler, var userInput: String?) : JavaCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?): Any? {
                val prompt: String = if (
                    parameters != null &&
                    parameters.length() > 0 &&
                    parameters[0] != null
                ) {
                    parameters[0].toString()
                } else {
                    "?"
                }

                userInput = null
                val message: Message = handler.obtainMessage(
                    STATUS_PROMPT,
                    prompt
                )
                handler.sendMessage(message)
                while (userInput == null) { /* loop */ }

                return userInput
            }
        }
    }
}