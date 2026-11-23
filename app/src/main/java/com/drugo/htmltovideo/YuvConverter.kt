package com.drugo.htmltovideo

import android.media.Image

/** Convert RGBA pixels to an encoder's YUV_420_888 planes, respecting strides. */
object YuvConverter {
    private fun clamp(v: Int) = v.coerceIn(0, 255)
    fun write(pixels: IntArray, w: Int, h: Int, image: Image) {
        val planes = image.planes
        require(planes.size == 3) { "YUV_420_888 required" }
        val yB=planes[0].buffer; val uB=planes[1].buffer; val vB=planes[2].buffer
        val yr=planes[0].rowStride; val yp=planes[0].pixelStride
        val ur=planes[1].rowStride; val up=planes[1].pixelStride
        val vr=planes[2].rowStride; val vp=planes[2].pixelStride
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                var rs=0;var gs=0;var bs=0
                for (dy in 0..1) for (dx in 0..1) {
                    val col=pixels[(y+dy)*w+x+dx]
                    val r=col shr 16 and 255; val g=col shr 8 and 255; val b=col and 255
                    val yy=clamp(((66*r +129*g +25*b +128) shr 8)+16)
                    yB.put((y+dy)*yr+(x+dx)*yp,yy.toByte())
                    rs+=r;gs+=g;bs+=b
                }
                val r=rs/4;val g=gs/4;val b=bs/4
                val u=clamp(((-38*r-74*g+112*b+128) shr 8)+128)
                val v=clamp(((112*r-94*g-18*b+128) shr 8)+128)
                uB.put((y/2)*ur+(x/2)*up,u.toByte())
                vB.put((y/2)*vr+(x/2)*vp,v.toByte())
            }
        }
    }
}
