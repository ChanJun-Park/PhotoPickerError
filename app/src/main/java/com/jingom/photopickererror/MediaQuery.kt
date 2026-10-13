package com.jingom.photopickererror

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore

data class PhotoItem(
	val id: Long,
	val uri: Uri,
)

/**
 * Query used by the in-app gallery picker: images collection, empty selection, sorted by date_added DESC.
 *
 * Errors are returned as [Result] instead of being swallowed, so "0 photos" and "query failed" are distinguishable.
 */
object MediaQuery {
	private const val ALL_PHOTO_BUCKET_ID = -1
	private const val SORT_ORDER = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"

	private val projection =
		arrayOf(
			MediaStore.Images.ImageColumns._ID,
			MediaStore.Images.ImageColumns.BUCKET_ID,
			MediaStore.Images.ImageColumns.DATE_MODIFIED,
			MediaStore.Images.ImageColumns.MIME_TYPE,
			MediaStore.Images.ImageColumns.ORIENTATION,
			MediaStore.Images.ImageColumns.DATE_TAKEN,
		)

	fun photos(
		context: Context,
		bucketId: Int = ALL_PHOTO_BUCKET_ID,
	): Result<List<PhotoItem>> =
		runCatching {
			context.contentResolver
				.query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					projection,
					if (bucketId == ALL_PHOTO_BUCKET_ID) "" else MediaStore.Images.ImageColumns.BUCKET_ID + "=" + bucketId,
					null,
					SORT_ORDER,
				).toPhotos()
		}

	/**
	 * Diagnostic: only the folder this app saved into. Files the app owns are visible without photo permission;
	 * after a reinstall clears the owner, only granted files are visible.
	 */
	fun savedFolder(
		context: Context,
		relativePath: String = MediaSaver.relativePath,
	): Result<List<PhotoItem>> =
		runCatching {
			context.contentResolver
				.query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					projection,
					MediaStore.MediaColumns.RELATIVE_PATH + "=?",
					arrayOf("$relativePath/"),
					SORT_ORDER,
				).toPhotos()
		}

	/** Whether QUERY_ARG_LATEST_SELECTION_ONLY is available — Android 15+, or Android 14 with the required Play system update. */
	fun supportsLatestSelection(): Boolean =
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
			SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 12

	/** Diagnostic: only what the user picked in the most recent selection. null when unsupported. */
	fun latestSelection(context: Context): Result<List<PhotoItem>>? {
		if (!supportsLatestSelection()) return null

		return runCatching {
			val args =
				Bundle().apply {
					putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, SORT_ORDER)
					putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true)
				}
			context.contentResolver
				.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, args, null)
				.toPhotos()
		}
	}

	private fun android.database.Cursor?.toPhotos(): List<PhotoItem> =
		use { cursor ->
			buildList {
				while (cursor != null && cursor.moveToNext()) {
					val id = cursor.getLong(0)
					add(PhotoItem(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)))
				}
			}
		}
}
