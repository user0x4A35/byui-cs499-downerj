package io.github.ascenderx.mobilescript.models.scripting

import android.os.Handler
import android.os.Message
import com.eclipsesource.v8.*
import java.util.concurrent.ConcurrentLinkedQueue

class ScriptEngine private constructor(handler: Handler) {
    companion object {
        private var instance: ScriptEngine? = null

        fun getInstance(handler: Handler): ScriptEngine {
            return if (instance != null) {
                instance as ScriptEngine
            } else {
                instance =
                    ScriptEngine(
                        handler
                    )
                instance as ScriptEngine
            }
        }
    }

    private val runnable: ScriptRunnable =
        ScriptRunnable(
            handler
        )
    private val thread: Thread = Thread(runnable)
    val commandHistory: MutableList<String> = mutableListOf()

    init {
        thread.start()
    }

    fun evaluate(command: String): Int {
        val historyIndex = commandHistory.size
        commandHistory.add(command)
        runnable.commands.add(command)
        return historyIndex
    }

    private class ScriptRunnable(private val handler: Handler) : Runnable {
        val commands: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        var running: Boolean = true
        // Cannot init runtime until run() is called in order to preserve Thread-safety for the
        // V8 runtime.
        lateinit var runtime: V8

        private fun executeCommand(command: String) {
            try {
                val result: String = (
                    runtime.executeScript(command) ?: "undefined"
                ).toString()
                sendResultMessage(result)
            } catch (exception: V8RuntimeException) {
                val error: String = exception.message ?: ""
                sendErrorMessage(error)
            }
        }

        private fun loop() {
            while (running) {
                // TODO: Implement full-stop on thread deletion.
                val command: String? = commands.poll()
                if (command != null) {
                    executeCommand(command)
                }
            }
            runtime.release(true)
        }

        fun sendResultMessage(result: String) {
            val message: Message = handler.obtainMessage(
                ScriptMessageStatus.RESULT.value,
                result
            )
            handler.sendMessage(message)
        }

        fun sendErrorMessage(error: String) {
            val message = handler.obtainMessage(
                ScriptMessageStatus.ERROR.value,
                error
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
            loop()
        }

        class PrintCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                if ((parameters == null) || (parameters.length() == 0)) {
                    return
                }
                val output = "${parameters[0]}"
                val message = handler.obtainMessage(ScriptMessageStatus.PRINT.value, output)
                handler.sendMessage(message)
            }
        }

        class PrintLineCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val output: String = if ((parameters == null) || (parameters.length() == 0)) {
                    "\n"
                } else {
                    "${parameters[0]}\n"
                }
                val message: Message = handler.obtainMessage(
                    ScriptMessageStatus.PRINT.value,
                    output
                )
                handler.sendMessage(message)
            }
        }

        class ClearCallback(private val handler: Handler) : JavaVoidCallback {
            override fun invoke(receiver: V8Object?, parameters: V8Array?) {
                val message: Message = handler.obtainMessage(
                    ScriptMessageStatus.CLEAR.value
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
    }
}