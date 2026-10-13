package com.jingom.photopickererror

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Writes the same line to the screen and to logcat (`adb logcat -s PPE`). Timestamps go down to milliseconds to line up with screen recordings.
 */
object DiagLog {
	private const val TAG = "PPE"
	private const val MAX_LINES = 40
	private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

	val lines = mutableStateListOf<String>()

	fun d(message: String) {
		Log.d(TAG, message)
		lines.add(0, "${LocalTime.now().format(timeFormat)}  $message")
		if (lines.size > MAX_LINES) lines.removeAt(lines.lastIndex)
	}
}
