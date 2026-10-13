package com.jingom.photopickererror

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jingom.photopickererror.ui.theme.PhotoPickerErrorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom photo grid with the same permission and reload flow as a typical in-app gallery picker.
 *
 * - Queries once when opened.
 * - "Select photos" on the banner re-requests the same permission array. Under partial access the OS shows
 *   the system photo selection screen. The selection may change even when the status stays PARTIAL,
 *   so the grid is re-queried after every result.
 * - onResume re-queries only when the permission status changed.
 */
class PickerActivity : ComponentActivity() {
	private var photos by mutableStateOf<List<PhotoItem>>(emptyList())
	private var status by mutableStateOf(MediaImagesPermissionStatus.DENIED)
	private var refreshTick by mutableIntStateOf(0)
	private var lastPermissionStatus: MediaImagesPermissionStatus? = null

	private val selectMorePhotos =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
			val current = MediaImagesPermission.status(this)
			DiagLog.d("[picker] select photos result map=${resultMap.mapKeys { it.key.removePrefix("android.permission.") }} → re-checked status=$current")
			lastPermissionStatus = current
			status = current
			reload("select photos result")
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		reload("first open")

		setContent {
			PhotoPickerErrorTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					Column(modifier = Modifier.padding(innerPadding)) {
						if (status == MediaImagesPermissionStatus.PARTIAL) {
							PartialAccessBanner(
								onSelectPhotos = {
									DiagLog.d("[picker] banner > select photos (re-request with the same array)")
									selectMorePhotos.launch(MediaImagesPermission.permissions)
								},
								onOpenSetting = ::openAppSetting,
							)
						}
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("Grid: ${photos.size} photos", modifier = Modifier.padding(8.dp).weight(1f))
							TextButton(onClick = { reload("manual") }) { Text("Reload") }
						}
						LazyVerticalGrid(
							columns = GridCells.Fixed(3),
							modifier = Modifier.fillMaxWidth().weight(1f),
							horizontalArrangement = Arrangement.spacedBy(2.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
						) {
							items(photos, key = { it.id }) { Thumbnail(it) }
						}
						DiagPanel(refreshTick = refreshTick, modifier = Modifier.height(260.dp))
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		val current = MediaImagesPermission.status(this)
		DiagLog.d("[picker] onResume status=$current (previous $lastPermissionStatus)")
		if (MediaImagesPermission.shouldReload(lastPermissionStatus, current)) {
			reload("onResume status changed")
		}
		lastPermissionStatus = current
		status = current
		refreshTick++
	}

	private fun reload(reason: String) {
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) { MediaQuery.photos(this@PickerActivity) }
			result
				.onSuccess {
					photos = it
					DiagLog.d("[picker] reload($reason) → ${it.size} photos")
				}.onFailure {
					DiagLog.d("[picker] reload($reason) failed ${it::class.simpleName}: ${it.message}")
				}
			refreshTick++
		}
	}
}

@Composable
private fun PartialAccessBanner(
	onSelectPhotos: () -> Unit,
	onOpenSetting: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF4D6)).padding(horizontal = 12.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text("Partial access — only selected photos are visible", modifier = Modifier.weight(1f))
		TextButton(onClick = onSelectPhotos) { Text("Select photos") }
		TextButton(onClick = onOpenSetting) { Text("Settings") }
	}
}

@Composable
private fun Thumbnail(item: PhotoItem) {
	val context = LocalContext.current
	val bitmap by produceState<Bitmap?>(null, item.uri) {
		value =
			withContext(Dispatchers.IO) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					runCatching { context.contentResolver.loadThumbnail(item.uri, Size(300, 300), null) }.getOrNull()
				} else {
					null
				}
			}
	}

	Box(modifier = Modifier.aspectRatio(1f).background(Color.LightGray), contentAlignment = Alignment.Center) {
		bitmap?.let {
			Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
		} ?: Text("#${item.id}")
	}
}
