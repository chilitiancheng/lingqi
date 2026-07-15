package com.lingqi.app.sleep

import java.util.concurrent.Executor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepFinishDispatcherTest {
    @Test
    fun dispatchQueuesFinalizationInsteadOfRunningOnCallerThread() {
        val executor = CapturingExecutor()
        val dispatcher = SleepFinishDispatcher(executor)
        var finalized = false

        assertTrue(dispatcher.dispatch { finalized = true })

        assertFalse(finalized)
        executor.runPending()
        assertTrue(finalized)
    }

    @Test
    fun dispatchRejectsDuplicateRequestWhileFinalizationIsPending() {
        val executor = CapturingExecutor()
        val dispatcher = SleepFinishDispatcher(executor)

        assertTrue(dispatcher.dispatch { })
        assertFalse(dispatcher.dispatch { })
    }

    @Test
    fun dispatchAcceptsAnotherRequestAfterFinalizationCompletes() {
        val executor = CapturingExecutor()
        val dispatcher = SleepFinishDispatcher(executor)

        assertTrue(dispatcher.dispatch { })
        executor.runPending()

        assertTrue(dispatcher.dispatch { })
    }

    private class CapturingExecutor : Executor {
        private var pending: Runnable? = null

        override fun execute(command: Runnable) {
            check(pending == null)
            pending = command
        }

        fun runPending() {
            val command = checkNotNull(pending)
            pending = null
            command.run()
        }
    }
}
