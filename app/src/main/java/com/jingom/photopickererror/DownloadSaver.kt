package com.jingom.photopickererror

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Downloads the same test images as [MediaSaver] into the public Pictures folder through DownloadManager.
 *
 * A MediaStore insert records the calling app as the owner, and after a reinstall the system photo picker shows
 * those files as selected. A separate folder is used to compare how DownloadManager-saved files behave.
 */
object DownloadSaver {
	const val FOLDER = "PhotoPickerErrorDM"
	val relativePath: String = Environment.DIRECTORY_PICTURES + "/" + FOLDER

	private const val POLL_INTERVAL_MS = 300L
	private const val TIMEOUT_MS = 30_000L
	private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

	suspend fun saveTestImages(
		context: Context,
		count: Int,
	): Result<Int> =
		runCatching {
			val manager = context.getSystemService(DownloadManager::class.java)
			val port = LocalImageServer.start()
			val stamp = LocalDateTime.now().format(nameFormat)

			val ids =
				List(count) { index ->
					val fileName = "PPEDM_${stamp}_$index.jpg"
					val request =
						DownloadManager
							.Request(Uri.parse("http://127.0.0.1:$port/$fileName"))
							.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "$FOLDER/$fileName")
							.setMimeType("image/jpeg")
							.setTitle(fileName)
							.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
					manager.enqueue(request).also { DiagLog.d("[dm] enqueue $fileName → id=$it") }
				}

			awaitAll(manager, ids)
		}

	/** Waits for all downloads and returns the success count. Failures and timeouts are logged with the reason. */
	private suspend fun awaitAll(
		manager: DownloadManager,
		ids: List<Long>,
	): Int {
		val pending = ids.toMutableSet()
		var succeeded = 0
		val deadline = System.currentTimeMillis() + TIMEOUT_MS

		while (pending.isNotEmpty() && System.currentTimeMillis() < deadline) {
			manager.query(DownloadManager.Query().setFilterById(*pending.toLongArray()))?.use { cursor ->
				while (cursor.moveToNext()) {
					val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
					when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
						DownloadManager.STATUS_SUCCESSFUL -> {
							pending.remove(id)
							succeeded++
							val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
							DiagLog.d("[dm] done id=$id local=$localUri mediaUri=${manager.getUriForDownloadedFile(id)}")
						}

						DownloadManager.STATUS_FAILED -> {
							pending.remove(id)
							val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
							DiagLog.d("[dm] failed id=$id reason=$reason")
						}
					}
				}
			}
			if (pending.isNotEmpty()) delay(POLL_INTERVAL_MS)
		}

		if (pending.isNotEmpty()) DiagLog.d("[dm] timed out ${pending.size} id=$pending")
		return succeeded
	}
}
