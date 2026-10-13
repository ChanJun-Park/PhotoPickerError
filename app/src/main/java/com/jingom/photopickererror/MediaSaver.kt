package com.jingom.photopickererror

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saves images with a MediaStore insert into MediaStore.Images (VOLUME_EXTERNAL)
 * and writes through the returned URI. Rows created this way are owned by this app.
 *
 * Uninstalling and reinstalling the app makes MediaProvider clear the owner. That state is the repro condition.
 */
object MediaSaver {
	const val FOLDER = "PhotoPickerError"
	val relativePath: String = Environment.DIRECTORY_PICTURES + "/" + FOLDER

	private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
	private val colors = intArrayOf(0xFFE57373.toInt(), 0xFF64B5F6.toInt(), 0xFF81C784.toInt(), 0xFFFFD54F.toInt(), 0xFFBA68C8.toInt())

	fun saveTestImages(
		context: Context,
		count: Int,
	): Result<Int> =
		runCatching {
			check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "RELATIVE_PATH requires API 29+" }

			val stamp = LocalDateTime.now().format(nameFormat)
			repeat(count) { index ->
				val fileName = "PPE_${stamp}_$index.jpg"
				val now = System.currentTimeMillis() / 1000
				val values =
					ContentValues().apply {
						put(MediaStore.Files.FileColumns.DATE_ADDED, now)
						put(MediaStore.Files.FileColumns.DATE_MODIFIED, now)
						put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
						put(MediaStore.Files.FileColumns.MIME_TYPE, "image/jpeg")
						put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
					}

				val uri =
					context.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), values)
						?: error("insert failed: $fileName")

				context.contentResolver.openOutputStream(uri)?.use { out ->
					label(fileName, colors[index % colors.size]).compress(Bitmap.CompressFormat.JPEG, 90, out)
				} ?: error("write failed: $uri")

				DiagLog.d("[save] $fileName → $uri")
			}
			count
		}

	/** Draws the file name into the image so it can be identified in the system photo picker. */
	fun label(
		text: String,
		background: Int,
	): Bitmap {
		val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
		Canvas(bitmap).apply {
			drawColor(background)
			val paint =
				Paint(Paint.ANTI_ALIAS_FLAG).apply {
					color = Color.WHITE
					textSize = 44f
					textAlign = Paint.Align.CENTER
				}
			drawText("PhotoPickerError", 400f, 360f, paint)
			drawText(text, 400f, 440f, paint)
		}
		return bitmap
	}
}
