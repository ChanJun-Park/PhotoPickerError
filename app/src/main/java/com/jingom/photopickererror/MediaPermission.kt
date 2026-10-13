package com.jingom.photopickererror

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/*
 * Photo permission handling of an app that keeps its own gallery picker:
 * the requested permission array, the status check, and the entry / result decisions.
 */

enum class MediaImagesPermissionStatus { GRANTED, PARTIAL, DENIED }

enum class PermissionAction { OPEN_PICKER, REQUEST, ALERT_REQUEST_AGAIN, ALERT_OPEN_SETTING }

object MediaImagesPermission {
	/** Requested together, in this order. */
	val permissions: Array<String> =
		buildList {
			when {
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
					add(Manifest.permission.READ_MEDIA_IMAGES)
					add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
				}

				Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> add(Manifest.permission.READ_MEDIA_IMAGES)

				else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				add(Manifest.permission.ACCESS_MEDIA_LOCATION)
			}
		}.toTypedArray()

	/**
	 * Keep the check order — READ_MEDIA_VISUAL_USER_SELECTED can also report granted under full access.
	 */
	fun status(context: Context): MediaImagesPermissionStatus =
		when {
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
				context.isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaImagesPermissionStatus.GRANTED

			Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
				context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaImagesPermissionStatus.PARTIAL

			Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
				context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaImagesPermissionStatus.GRANTED

			else -> MediaImagesPermissionStatus.DENIED
		}

	/** Rationale of the first permission: the user denied before and the app may still ask again. */
	fun canAskAgain(activity: Activity): Boolean = ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions.first())

	/** Decision before sending a request. */
	fun entry(
		status: MediaImagesPermissionStatus,
		deniedBefore: Boolean,
	): PermissionAction =
		when {
			status != MediaImagesPermissionStatus.DENIED -> PermissionAction.OPEN_PICKER
			deniedBefore -> PermissionAction.ALERT_REQUEST_AGAIN
			else -> PermissionAction.REQUEST
		}

	/** Decision after a request, based on the status re-read from the OS rather than the result map. */
	fun result(
		status: MediaImagesPermissionStatus,
		canAskAgain: Boolean,
	): PermissionAction =
		when {
			status != MediaImagesPermissionStatus.DENIED -> PermissionAction.OPEN_PICKER
			canAskAgain -> PermissionAction.ALERT_REQUEST_AGAIN
			else -> PermissionAction.ALERT_OPEN_SETTING
		}

	/** No reload on the first check (null previous). */
	fun shouldReload(
		previous: MediaImagesPermissionStatus?,
		current: MediaImagesPermissionStatus,
	): Boolean = previous != null && previous != current

	/** For the diagnostic panel: granted state of each permission. */
	fun grantTable(context: Context): List<Pair<String, Boolean>> =
		permissions.map { it.removePrefix("android.permission.") to context.isGranted(it) }

	private fun Context.isGranted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
