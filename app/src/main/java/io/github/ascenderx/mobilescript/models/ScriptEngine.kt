package io.github.ascenderx.mobilescript.models

import android.os.Handler
import android.os.Message
import android.util.Log
import com.eclipsesource.v8.*
import java.util.concurrent.ConcurrentLinkedQueue

class ScriptEngine private constructor(handler: Handler) {
    companion object {
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

    private val runnable: ScriptRunnable = ScriptRunnable(handler)
    private val thread: Thread = Thread(runnable)

    init {
        thread.start()
    }

    fun evaluate(command: String) {
        runnable.commands.add(command)
    }

    private class ScriptRunnable(private val handler: Handler) : Runnable {
        val commands: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

        override fun run() {
            val runtime: V8 = V8.createV8Runtime()
            runtime.registerJavaMethod(PrintCallback(handler), "print")
            runtime.registerJavaMethod(PrintLineCallback(handler), "println")
            runtime.registerJavaMethod(ClearCallback(handler), "clear")

            while (true) {
                val command: String? = commands.poll()
                if (command != null) {
                    var result: Any?
                    var errorMessage: String?
                    var message: Message

                    try {
                        result = runtime.executeScript(command) ?: "undefined"
                        message = handler.obtainMessage(
                            ScriptMessageStatus.RESULT.value,
                            result.toString()
                        )
                    } catch (exception: V8RuntimeException) {
                        errorMessage = exception.message
                        message = handler.obtainMessage(
                            ScriptMessageStatus.ERROR.value,
                            errorMessage
                        )
                    }
                    handler.sendMessage(message)
                }
            }
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
    }
}

enum class ScriptMessageStatus(val value: Int) {
    ERROR(-1),
    RESULT(0),
    PRINT(1),
    CLEAR(2)
}