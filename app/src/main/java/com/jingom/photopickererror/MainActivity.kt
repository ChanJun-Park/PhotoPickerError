package com.jingom.photopickererror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jingom.photopickererror.ui.theme.PhotoPickerErrorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point of the "Add photos" flow, including the permission gate in front of the picker.
 *
 * The picker opens only after access is granted. The two alert branches (request again / open settings)
 * differ only in what the confirm button does, so the branch is shown in the alert text and the diagnostic log.
 */
class MainActivity : ComponentActivity() {
	private var alert by mutableStateOf<PermissionAction?>(null)

	/** Bumped on every onResume and permission result to make the diagnostic panel re-read. */
	private var refreshTick by mutableIntStateOf(0)

	private val requestPermissions =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
			val status = MediaImagesPermission.status(this)
			val canAskAgain = MediaImagesPermission.canAskAgain(this)
			DiagLog.d("[gate] request result map=${resultMap.mapKeys { it.key.removePrefix("android.permission.") }} → re-checked status=$status canAskAgain=$canAskAgain")
			refreshTick++
			handle(MediaImagesPermission.result(status, canAskAgain))
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			PhotoPickerErrorTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					Column(
						modifier = Modifier.padding(innerPadding).padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Button(onClick = ::ensureAccess, modifier = Modifier.fillMaxWidth()) {
							Text("Add photos")
						}
						OutlinedButton(onClick = ::saveTestImages, modifier = Modifier.fillMaxWidth()) {
							Text("Save ${SAVE_COUNT} images via MediaStore (Pictures/${MediaSaver.FOLDER})")
						}
						OutlinedButton(onClick = ::downloadTestImages, modifier = Modifier.fillMaxWidth()) {
							Text("Save ${SAVE_COUNT} images via DownloadManager (Pictures/${DownloadSaver.FOLDER})")
						}
						DiagPanel(refreshTick = refreshTick, modifier = Modifier.weight(1f))
					}

					alert?.let { current ->
						AlertDialog(
							onDismissRequest = { alert = null },
							text = { Text("Photo access is required.\n(${if (current == PermissionAction.ALERT_REQUEST_AGAIN) "request again" else "open settings"})") },
							confirmButton = {
								TextButton(onClick = {
									alert = null
									onAlertConfirmed(current)
								}) { Text("OK") }
							},
							dismissButton = { TextButton(onClick = { alert = null }) { Text("Cancel") } },
						)
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		refreshTick++
	}

	private fun saveTestImages() {
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) { MediaSaver.saveTestImages(this@MainActivity, SAVE_COUNT) }
			result
				.onSuccess { DiagLog.d("[save] saved ${it} images") }
				.onFailure { DiagLog.d("[save] failed ${it::class.simpleName}: ${it.message}") }
			refreshTick++
		}
	}

	private fun downloadTestImages() {
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) { DownloadSaver.saveTestImages(this@MainActivity, SAVE_COUNT) }
			result
				.onSuccess { DiagLog.d("[dm] saved ${it}/${SAVE_COUNT} images") }
				.onFailure { DiagLog.d("[dm] failed ${it::class.simpleName}: ${it.message}") }
			refreshTick++
		}
	}

	private fun ensureAccess() {
		val status = MediaImagesPermission.status(this)
		val canAskAgain = MediaImagesPermission.canAskAgain(this)
		val action = MediaImagesPermission.entry(status, canAskAgain)
		DiagLog.d("[gate] entry status=$status canAskAgain=$canAskAgain → $action")
		handle(action)
	}

	private fun handle(action: PermissionAction) {
		when (action) {
			PermissionAction.OPEN_PICKER -> startActivity(Intent(this, PickerActivity::class.java))
			PermissionAction.REQUEST -> launchRequest()
			PermissionAction.ALERT_REQUEST_AGAIN, PermissionAction.ALERT_OPEN_SETTING -> alert = action
		}
	}

	private fun onAlertConfirmed(action: PermissionAction) {
		DiagLog.d("[gate] alert confirmed $action")
		when (action) {
			PermissionAction.ALERT_REQUEST_AGAIN -> launchRequest()
			PermissionAction.ALERT_OPEN_SETTING -> openAppSetting()
			else -> Unit
		}
	}

	private fun launchRequest() {
		DiagLog.d("[gate] requesting permissions")
		requestPermissions.launch(MediaImagesPermission.permissions)
	}
}

private const val SAVE_COUNT = 5

internal fun ComponentActivity.openAppSetting() {
	runCatching {
		startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
	}
}
