package com.europartes.mobile.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.europartes.mobile.data.local.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starts background synchronization and tells open screens when new server data is in Room. */
object SyncCoordinator {
    private const val WORK_NAME = "europartes_offline_sync"
    private const val KEY_FORCE_DOWNLOAD = "force_download"
    private const val DOWNLOAD_MIN_INTERVAL_MS = 2 * 60 * 1000L

    private val mutableRunning = MutableStateFlow(false)
    val isRunning = mutableRunning.asStateFlow()

    private val mutableDataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataChanged = mutableDataChanged.asSharedFlow()

    private var lastDownloadAtEpochMs = 0L

    /**
     * WorkManager keeps this request across app and device restarts and runs it as soon as there is
     * a network, so queued changes are sent even if the app is closed when connectivity returns.
     */
    fun request(context: Context, forceDownload: Boolean = false) {
        val work = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_FORCE_DOWNLOAD to forceDownload))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    internal fun shouldDownload(force: Boolean, sentChanges: Boolean, now: Long = System.currentTimeMillis()): Boolean =
        force || sentChanges || now - lastDownloadAtEpochMs >= DOWNLOAD_MIN_INTERVAL_MS

    internal fun markDownloaded(now: Long = System.currentTimeMillis()) { lastDownloadAtEpochMs = now }
    internal fun setRunning(running: Boolean) { mutableRunning.value = running }
    internal fun notifyDataChanged() { mutableDataChanged.tryEmit(Unit) }
    /** Discarding a queued change alters what screens must draw, even without a sync run. */
    fun notifyChangedAfterDiscard() = notifyDataChanged()
    internal fun forceDownloadRequested(worker: CoroutineWorker): Boolean = worker.inputData.getBoolean(KEY_FORCE_DOWNLOAD, false)
}

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tokenStore = TokenStore(applicationContext)
        val role = tokenStore.roleForLocalPartition() ?: return Result.success()
        SyncCoordinator.setRunning(true)
        return try {
            val sync = SyncEngine(applicationContext).run()
            // An expired session waits for the next login; retrying cannot fix it.
            if (sync.sessionRejected) return Result.success()
            if (sync.confirmed > 0 || sync.needsAttention > 0) SyncCoordinator.notifyDataChanged()
            if (sync.interrupted) return Result.retry()
            val force = SyncCoordinator.forceDownloadRequested(this)
            if (SyncCoordinator.shouldDownload(force, sentChanges = sync.confirmed > 0)) {
                val complete = OfflineDataPrefetcher(applicationContext).run(role)
                SyncCoordinator.notifyDataChanged()
                if (!complete) return Result.retry()
                SyncCoordinator.markDownloaded()
            }
            Result.success()
        } finally {
            SyncCoordinator.setRunning(false)
        }
    }
}
