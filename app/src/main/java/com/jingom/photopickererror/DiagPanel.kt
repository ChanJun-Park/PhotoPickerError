package com.jingom.photopickererror

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DiagSnapshot(
	val status: MediaImagesPermissionStatus,
	val canAskAgain: Boolean,
	val grants: List<Pair<String, Boolean>>,
	val allQuery: String,
	val latestQuery: String,
	val savedQuery: String,
	val dmSavedQuery: String,
)

/**
 * Shows the permission status reported by the OS next to the photo counts MediaStore returns at the same moment.
 * Re-reads whenever [refreshTick] changes.
 */
@Composable
fun DiagPanel(
	refreshTick: Int,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	var snapshot by remember { mutableStateOf<DiagSnapshot?>(null) }

	LaunchedEffect(refreshTick) {
		val activity = context as Activity
		val status = MediaImagesPermission.status(context)
		val canAskAgain = MediaImagesPermission.canAskAgain(activity)
		val grants = MediaImagesPermission.grantTable(context)
		val queries =
			withContext(Dispatchers.IO) {
				listOf(
					MediaQuery.photos(context).describe(),
					MediaQuery.latestSelection(context)?.describe() ?: "not supported on this device",
					MediaQuery.savedFolder(context).describe(),
					MediaQuery.savedFolder(context, DownloadSaver.relativePath).describe(),
				)
			}
		snapshot = DiagSnapshot(status, canAskAgain, grants, queries[0], queries[1], queries[2], queries[3])
	}

	val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)

	Column(modifier = modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(8.dp)) {
		snapshot?.let { s ->
			Text("SDK ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}", style = mono)
			Text("status=${s.status}  canAskAgain=${s.canAskAgain}", style = mono)
			s.grants.forEach { (name, granted) -> Text("  ${if (granted) "✅" else "❌"} $name", style = mono) }
			Text("query (all)           : ${s.allQuery}", style = mono)
			Text("query (latest select) : ${s.latestQuery}", style = mono)
			Text("query (MediaStore dir): ${s.savedQuery}", style = mono)
			Text("query (DM dir)        : ${s.dmSavedQuery}", style = mono)
		}
		HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
		LazyColumn {
			items(DiagLog.lines) { Text(it, style = mono) }
		}
	}
}

private fun Result<List<PhotoItem>>.describe(): String =
	fold(
		onSuccess = { "${it.size} photos" },
		onFailure = { "failed ${it::class.simpleName}: ${it.message}" },
	)
