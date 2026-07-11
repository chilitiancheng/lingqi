package com.lingqi.app.wellness

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.lingqi.app.LingqiApplication

class WellnessSummaryProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val appContext = requireNotNull(context).applicationContext
        enforceLinglianCaller(appContext.packageManager, appContext.packageName)
        val application = appContext as? LingqiApplication
            ?: throw IllegalStateException("Lingqi application is unavailable")
        if (!application.container.repository.preferences().linglianWellnessSharingEnabled) {
            throw SecurityException("Wellness summary sharing is disabled by the user")
        }

        val parsed = WellnessQueryPolicy.parse(
            pathSegments = uri.pathSegments,
            queryParameters = uri.queryParameterNames.associateWith(uri::getQueryParameters),
            projection = projection,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = sortOrder
        )
        val database = application.container.database
        return when (parsed.kind) {
            WellnessSummaryKind.SLEEP -> MatrixCursor(
                WellnessProviderContract.SLEEP_COLUMNS.toTypedArray()
            ).apply {
                database.sleepSummaries(parsed.limit).forEach { summary ->
                    addRow(arrayOf<Any?>(
                        summary.sessionId, summary.startedAt, summary.endedAt, summary.durationSeconds,
                        summary.score, summary.coverage, summary.calibrationNight
                    ))
                }
            }
            WellnessSummaryKind.MEDITATION -> MatrixCursor(
                WellnessProviderContract.MEDITATION_COLUMNS.toTypedArray()
            ).apply {
                database.meditationSummaries(parsed.limit).forEach { summary ->
                    addRow(arrayOf<Any?>(
                        summary.sessionId, summary.practiceId, summary.startedAt, summary.endedAt,
                        summary.plannedSeconds, summary.actualSeconds, summary.completionRate
                    ))
                }
            }
        }
    }

    override fun getType(uri: Uri): String = when (uri.pathSegments) {
        listOf("sleep", "recent") -> "vnd.android.cursor.dir/vnd.com.lingqi.sleep-summary"
        listOf("meditation", "recent") -> "vnd.android.cursor.dir/vnd.com.lingqi.meditation-summary"
        else -> throw IllegalArgumentException("Unsupported wellness summary path")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = unsupported()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = unsupported()

    private fun enforceLinglianCaller(packageManager: PackageManager, providerPackage: String) {
        val identity = CallerIdentity(
            uidPackages = packageManager.getPackagesForUid(Binder.getCallingUid())?.toSet().orEmpty(),
            reportedCallingPackage = callingPackage,
            signaturesMatch = packageManager.checkSignatures(
                providerPackage,
                WellnessProviderContract.LINGLIAN_PACKAGE
            ) == PackageManager.SIGNATURE_MATCH
        )
        if (!LinglianCallerAuthorization.isAllowed(identity)) {
            throw SecurityException("Caller is not the authorized Linglian app")
        }
    }

    private fun <T> unsupported(): T =
        throw UnsupportedOperationException("Wellness summaries are read-only")
}
