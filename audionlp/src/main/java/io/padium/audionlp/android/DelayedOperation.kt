package io.padium.audionlp.android

import android.content.Context
import android.os.Handler
import android.util.Log
import java.util.*

class DelayedOperation(private val context: Context, private val tag: String,
                       private val delayInMilliseconds: Long) {
    companion object {
        private val TAG = DelayedOperation::class.java.simpleName
    }

    interface Operation {
        fun onDelayedOperation()
        fun shouldExecuteDelayedOperation(): Boolean
    }

    private lateinit var operation: Operation
    private lateinit var timer: Timer
    private var started: Boolean = false

    init {
        if (delayInMilliseconds <= 0) {
            throw IllegalArgumentException("The delay in milliseconds must be > 0")
        }

        Log.d(TAG, "Created delayed operation with tag: $tag")
    }

    fun start(operation: Operation) {
        Log.d(TAG, "starting delayed operation with tag: $tag")

        this.operation = operation
        cancel()
        started = true
        resetTimer()
    }

    fun resetTimer() {
        if (!started) return

        if(!::operation.isInitialized)
            return

        if(::timer.isInitialized) {
            timer.cancel()
        }

        Log.d(TAG, "resetting delayed operation with tag: $tag")
        timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (operation.shouldExecuteDelayedOperation()) {
                    Log.d(TAG, "executing delayed operation with tag: $tag")
                    Handler(context.mainLooper).post { operation.onDelayedOperation() }
                }
                cancel()
            }
        }, delayInMilliseconds)
    }

    fun cancel() {
        Log.d(TAG, "cancelled delayed operation with tag: $tag")
        if(::timer.isInitialized) {
            timer.cancel()
        }
        started = false
    }
}