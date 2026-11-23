package com.drugo.htmltovideo

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Prototype: frame-based WebView capture -> MediaCodec (YUV420) -> MediaMuxer.
 * This uses no MediaProjection and no remote rendering service.
 * WebView.draw() may return blank/stale Canvas content on some Android WebView versions.
 * Verify the exported MP4 on the target device before claiming production support.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var previewHolder: FrameLayout
    private lateinit var durationField: EditText
    private lateinit var resolutionSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var exportButton: Button
    private var html: String = ""
    private var pageReady = false
    private var running = false
    private var videoW = 720
    private var videoH = 1280
    private val fps = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        html = assets.open("demo.html").bufferedReader().use { it.readText() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        setContentView(root)

        val heading = TextView(this).apply {
            text = "DRUGO HTML TO VIDEO  ·  prueba técnica"
            textSize = 19f
        }
        root.addView(heading)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tools)
        val importBtn = Button(this).apply { text = "Importar HTML" }
        tools.addView(importBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        durationField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("45")
            hint = "Segundos"
        }
        tools.addView(durationField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f))
        resolutionSpinner = Spinner(this)
        resolutionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("720p", "1080p"))
        tools.addView(resolutionSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.65f))
        previewHolder = FrameLayout(this)
        root.addView(previewHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                status.text = "HTML cargado. Comprueba la vista previa y exporta."
            }
        }
        previewHolder.addView(web)
        previewHolder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutWeb() }

        exportButton = Button(this).apply { text = "Exportar MP4 (sin audio)" }
        root.addView(exportButton)
        status = TextView(this).apply { text = "Cargando demostración..." }
        root.addView(status)

        importBtn.setOnClickListener {
            if (running) return@setOnClickListener
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html", "application/xhtml+xml", "text/plain"))
            }
            startActivityForResult(intent, 101)
        }
        exportButton.setOnClickListener { beginExport() }
        loadHtml()
    }

    private fun layoutWeb() {
        if (running) return
        val w = previewHolder.width
        val h = previewHolder.height
        if (w == 0 || h == 0) return
        // Offscreen layout is video-sized; display it scaled to fit the visible preview.
        val scale = minOf(w.toFloat() / videoW, h.toFloat() / videoH)
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = scale
        web.scaleY = scale
        web.layoutParams = FrameLayout.LayoutParams(videoW, videoH).apply {
            leftMargin = ((w - videoW * scale) / 2f).roundToInt()
            topMargin = ((h - videoH * scale) / 2f).roundToInt()
        }
    }

    private fun loadHtml() {
        pageReady = false
        val prepared = HtmlAdapter.adapt(html)
        web.loadDataWithBaseURL("https://appassets.androidplatform.net/", prepared, "text/html", "UTF-8", null)
    }

    @Deprecated("Legacy result for a self-contained Android prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 101 || resultCode != RESULT_OK) return
        try {
            val uri = data?.data ?: return
            val source = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("No se puede abrir el HTML")
            if (source.length > 3_000_000) error("El archivo supera el límite de 3 MB de la prueba")
            html = source
            loadHtml()
        } catch (e: Exception) {
            status.text = "Error de importación: ${e.message}"
        }
    }

    private fun beginExport() {
        if (running || !pageReady) return
        val seconds = durationField.text.toString().toIntOrNull()
        if (seconds == null || seconds !in 1..600) {
            Toast.makeText(this, "Introduce una duración entre 1 y 600 segundos", Toast.LENGTH_LONG).show()
            return
        }
        val resolution = if (resolutionSpinner.selectedItemPosition == 0) 720 else 1080
        videoW = resolution
        videoH = if (resolution == 720) 1280 else 1920
        layoutWeb()
        running = true
        exportButton.isEnabled = false
        status.text = "Preparando codificador..."
        // We intentionally stop if deterministic bridge is not installed.
        web.evaluateJavascript("typeof window.drugoRenderAt === 'function'") { answer ->
            if (answer != "true") {
                running = false
                exportButton.isEnabled = true
                status.text = "Este HTML necesita un controlador de tiempo (drugoRenderAt) para exportación sincronizada."
            } else {
                web.postDelayed({ Thread { encode(seconds) }.start() }, 350)
            }
        }
    }

    private fun captureAt(ms: Long, bitmap: Bitmap): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        runOnUiThread {
            try {
                // callback signals the synchronous JS render operation completed.
                web.evaluateJavascript("window.__drugoDurationMs=${durationField.text}; window.drugoRenderAt(${ms}); 'ok'") {
                    try {
                        val c = Canvas(bitmap)
                        c.drawColor(android.graphics.Color.BLACK)
                        web.draw(c)
                        success = true
                    } catch (_: Exception) {
                        success = false
                    } finally { latch.countDown() }
                }
            } catch (_: Exception) { latch.countDown() }
        }
        return latch.await(10, TimeUnit.SECONDS) && success
    }

    private fun encode(seconds: Int) {
        val frames = seconds * fps
        val bitmap = Bitmap.createBitmap(videoW, videoH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(videoW * videoH)
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var destination: Uri? = null
        var fd: android.os.ParcelFileDescriptor? = null
        var muxStarted = false
        var track = -1
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "DRUGO_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DRUGO HTML TO VIDEO")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            destination = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el vídeo")
            fd = contentResolver.openFileDescriptor(destination, "w")
                ?: error("No se pudo abrir la salida MP4")
            muxer = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoW, videoH).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, if (videoW == 1080) 8_000_000 else 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            fun drain(end: Boolean): Boolean {
                while (true) {
                    val i = codec!!.dequeueOutputBuffer(info, if (end) 10000 else 0)
                    if (i == MediaCodec.INFO_TRY_AGAIN_LATER) return false
                    if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer!!.addTrack(codec!!.outputFormat)
                        muxer!!.start()
                        muxStarted = true
                    } else if (i >= 0) {
                        val b = codec!!.getOutputBuffer(i)!!
                        if (info.size > 0 && muxStarted) {
                            b.position(info.offset)
                            b.limit(info.offset + info.size)
                            muxer!!.writeSampleData(track, b, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec!!.releaseOutputBuffer(i, false)
                        if (eos) return true
                    }
                }
            }
            for (frame in 0 until frames) {
                val timestampMs = frame.toLong() * 1000 / fps
                if (!captureAt(timestampMs, bitmap)) error("No se pudo renderizar el fotograma ${frame + 1}")
                bitmap.getPixels(pixels, 0, videoW, 0, 0, videoW, videoH)
                var inputIndex = codec.dequeueInputBuffer(10000)
                while (inputIndex < 0) {
                    drain(false)
                    inputIndex = codec.dequeueInputBuffer(10000)
                }
                val image = codec.getInputImage(inputIndex) ?: error("El codificador no admite entrada YUV flexible")
                try { YuvConverter.write(pixels, videoW, videoH, image) } finally { image.close() }
                codec.queueInputBuffer(inputIndex, 0, videoW * videoH * 3 / 2,
                    frame.toLong() * 1_000_000L / fps, 0)
                drain(false)
                if (frame % 90 == 0 || frame == frames - 1) {
                    val progress = frame + 1
                    runOnUiThread { status.text = "Exportando: ${progress}/${frames} fotogramas" }
                }
            }
            var eosIndex = codec.dequeueInputBuffer(10000)
            while (eosIndex < 0) { drain(false); eosIndex = codec.dequeueInputBuffer(10000) }
            codec.queueInputBuffer(eosIndex, 0, 0, frames.toLong() * 1_000_000L / fps,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            var done = false
            var tries = 0
            while (!done && tries++ < 1500) done = drain(true)
            if (!done || !muxStarted) error("No se completó el contenedor MP4")
            muxer.stop()
            muxStarted = false
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(destination, values, null, null)
            runOnUiThread { status.text = "MP4 guardado en Movies/DRUGO HTML TO VIDEO (sin audio)" }
        } catch (e: Exception) {
            try { if (muxStarted) muxer?.stop() } catch (_: Exception) {}
            destination?.let { contentResolver.delete(it, null, null) }
            runOnUiThread { status.text = "Exportación fallida: ${e.javaClass.simpleName}: ${e.message}" }
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { fd?.close() } catch (_: Exception) {}
            bitmap.recycle()
            runOnUiThread { running = false; exportButton.isEnabled = true }
        }
    }
}
