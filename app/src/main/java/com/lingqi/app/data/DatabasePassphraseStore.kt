package com.lingqi.app.data

internal fun requireDatabasePassphrase(passphrase: ByteArray): ByteArray {
    require(passphrase.isNotEmpty()) { "Lingqi database passphrase must be non-empty" }
    return passphrase
}

private val databasePassphraseCreationLock = Any()

internal class DatabasePassphraseStore(
    private val readWrapped: () -> String?,
    private val persistWrapped: (String) -> Boolean,
    private val generatePassphrase: () -> ByteArray,
    private val wrap: (ByteArray) -> String,
    private val unwrap: (String) -> ByteArray
) {
    fun loadOrCreate(): ByteArray = synchronized(databasePassphraseCreationLock) {
        readWrapped()?.let { return@synchronized unwrap(it) }

        val generated = generatePassphrase()
        val wrapped = wrap(generated)
        check(persistWrapped(wrapped)) { "Unable to persist the Lingqi database passphrase" }
        val persisted = readWrapped()
            ?: error("The Lingqi database passphrase was not readable after persistence")
        val confirmed = unwrap(persisted)
        check(generated.contentEquals(confirmed)) {
            "The persisted Lingqi database passphrase did not match the generated passphrase"
        }
        confirmed
    }
}
