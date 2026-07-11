package com.lingqi.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DatabasePassphraseStoreTest {
    @Test
    fun emptyDatabasePassphraseIsRejected() {
        val error = runCatching { requireDatabasePassphrase(byteArrayOf()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("non-empty"))
    }

    @Test
    fun persistenceFailureDoesNotReturnNewPassphrase() {
        val generated = byteArrayOf(1, 2, 3)
        var persistedValue: String? = null
        val store = DatabasePassphraseStore(
            readWrapped = { null },
            persistWrapped = { value ->
                persistedValue = value
                false
            },
            generatePassphrase = { generated },
            wrap = { "wrapped-${it.joinToString()}" },
            unwrap = { error("No existing passphrase expected") }
        )

        val error = runCatching { store.loadOrCreate() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("persist"))
        assertEquals("wrapped-1, 2, 3", persistedValue)
    }

    @Test
    fun successfulPersistenceReturnsGeneratedPassphrase() {
        val generated = byteArrayOf(4, 5, 6)
        var persisted: String? = null
        val store = DatabasePassphraseStore(
            readWrapped = { persisted },
            persistWrapped = {
                persisted = it
                true
            },
            generatePassphrase = { generated },
            wrap = { "wrapped" },
            unwrap = { generated }
        )

        assertArrayEquals(generated, store.loadOrCreate())
    }

    @Test
    fun existingWrappedPassphraseDoesNotWritePreferences() {
        var writes = 0
        val store = DatabasePassphraseStore(
            readWrapped = { "existing" },
            persistWrapped = {
                writes += 1
                true
            },
            generatePassphrase = { error("Existing passphrase must be reused") },
            wrap = { error("Existing passphrase must not be wrapped again") },
            unwrap = { byteArrayOf(7, 8, 9) }
        )

        assertArrayEquals(byteArrayOf(7, 8, 9), store.loadOrCreate())
        assertEquals(0, writes)
    }

    @Test
    fun concurrentFirstRunCallersReturnTheSamePersistedPassphrase() {
        val wrapped = AtomicReference<String?>(null)
        val generatedCount = AtomicInteger(0)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit<ByteArray> {
                    val store = DatabasePassphraseStore(
                        readWrapped = { wrapped.get() },
                        persistWrapped = { value ->
                            wrapped.set(value)
                            true
                        },
                        generatePassphrase = {
                            byteArrayOf(generatedCount.incrementAndGet().toByte())
                        },
                        wrap = { value -> value.joinToString(",") },
                        unwrap = { value -> value.split(',').map(String::toByte).toByteArray() }
                    )
                    ready.countDown()
                    start.await(2, TimeUnit.SECONDS)
                    store.loadOrCreate()
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            val first = futures[0].get(2, TimeUnit.SECONDS)
            val second = futures[1].get(2, TimeUnit.SECONDS)

            assertArrayEquals(first, second)
            assertEquals(1, generatedCount.get())
            assertEquals(first.joinToString(","), wrapped.get())
        } finally {
            pool.shutdownNow()
        }
    }
}
