package com.drugo.htmltovideo

import android.media.Image

/**
 * Fast BT.601 limited-range ARGB -> YUV420 conversion.
 * Processes each 2x2 block without allocating temporary objects or nested loops.
 * Honors independent row and pixel strides of encoder planes.
 */
object YuvConverter {
    private fun byte8(v: Int): Byte = v.coerceIn(0, 255).toByte()

    fun write(pixels: IntArray, w: Int, h: Int, image: Image) {
        require(w % 2 == 0 && h % 2 == 0)
        val planes = image.planes
        require(planes.size == 3) { "YUV_420_888 required" }
        val y = planes[0].buffer
        val u = planes[1].buffer
        val v = planes[2].buffer
        val yr = planes[0].rowStride
        val yp = planes[0].pixelStride
        val ur = planes[1].rowStride
        val up = planes[1].pixelStride
        val vr = planes[2].rowStride
        val vp = planes[2].pixelStride

        var row = 0
        while (row < h) {
            val first = row * w
            val second = first + w
            val y0 = row * yr
            val y1 = y0 + yr
            val uvRow = (row / 2)
            var col = 0
            while (col < w) {
                val a = pixels[first + col]
                val b = pixels[first + col + 1]
                val c = pixels[second + col]
                val d = pixels[second + col + 1]
                val ar = (a ushr 16) and 255; val ag = (a ushr 8) and 255; val ab = a and 255
                val br = (b ushr 16) and 255; val bg = (b ushr 8) and 255; val bb = b and 255
                val cr = (c ushr 16) and 255; val cg = (c ushr 8) and 255; val cb = c and 255
                val dr = (d ushr 16) and 255; val dg = (d ushr 8) and 255; val db = d and 255
                val x = col * yp
                y.put(y0 + x, byte8(((66 * ar + 129 * ag + 25 * ab + 128) shr 8) + 16))
                y.put(y0 + x + yp, byte8(((66 * br + 129 * bg + 25 * bb + 128) shr 8) + 16))
                y.put(y1 + x, byte8(((66 * cr + 129 * cg + 25 * cb + 128) shr 8) + 16))
                y.put(y1 + x + yp, byte8(((66 * dr + 129 * dg + 25 * db + 128) shr 8) + 16))
                val rr = (ar + br + cr + dr + 2) shr 2
                val gg = (ag + bg + cg + dg + 2) shr 2
                val bbAvg = (ab + bb + cb + db + 2) shr 2
                val chromaCol = (col / 2)
                u.put(uvRow * ur + chromaCol * up,
                    byte8(((-38 * rr - 74 * gg + 112 * bbAvg + 128) shr 8) + 128))
                v.put(uvRow * vr + chromaCol * vp,
                    byte8(((112 * rr - 94 * gg - 18 * bbAvg + 128) shr 8) + 128))
                col += 2
            }
            row += 2
        }
    }
}
