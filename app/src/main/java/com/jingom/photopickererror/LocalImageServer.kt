package com.jingom.photopickererror

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 127.0.0.1 HTTP server that serves the images DownloadManager downloads.
 *
 * DownloadManager only accepts http(s) URLs. An external URL could fail depending on the device network or a proxy,
 * so the app serves them itself. Each GET /<name>.jpg returns a JPEG with the file name drawn on it.
 */
object LocalImageServer {
	private var server: ServerSocket? = null

	/** Starts the server (or reuses the running one) and returns its port. */
	@Synchronized
	fun start(): Int {
		server?.let { return it.localPort }

		val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
		server = socket
		thread(name = "ppe-local-http", isDaemon = true) {
			while (!socket.isClosed) {
				runCatching { socket.accept() }.getOrNull()?.let { client -> thread(isDaemon = true) { serve(client) } }
			}
		}
		DiagLog.d("[http] 127.0.0.1:${socket.localPort} started")
		return socket.localPort
	}

	private fun serve(client: Socket) {
		client.use {
			val requestLine = it.getInputStream().bufferedReader().readLine().orEmpty()
			val fileName = requestLine.split(" ").getOrNull(1)?.trimStart('/').orEmpty().ifEmpty { "unknown.jpg" }

			val body =
				ByteArrayOutputStream().use { out ->
					MediaSaver.label(fileName, 0xFF4DB6AC.toInt()).compress(Bitmap.CompressFormat.JPEG, 90, out)
					out.toByteArray()
				}

			it.getOutputStream().apply {
				write(
					(
						"HTTP/1.1 200 OK\r\n" +
							"Content-Type: image/jpeg\r\n" +
							"Content-Length: ${body.size}\r\n" +
							"Connection: close\r\n\r\n"
					).toByteArray(),
				)
				write(body)
				flush()
			}
		}
	}
}
