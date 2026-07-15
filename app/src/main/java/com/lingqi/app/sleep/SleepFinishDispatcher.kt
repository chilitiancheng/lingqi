package com.lingqi.app.sleep

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class SleepFinishDispatcher(
    private val executor: Executor
) {
    private val finishing = AtomicBoolean(false)

    fun dispatch(finalize: () -> Unit): Boolean {
        if (!finishing.compareAndSet(false, true)) return false
        executor.execute {
            try {
                finalize()
            } finally {
                finishing.set(false)
            }
        }
        return true
    }
}
