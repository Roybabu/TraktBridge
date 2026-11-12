package com.traktbridge.app

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

object PairingQr {
    fun decode(bitmap:Bitmap):String {
        require(bitmap.width in 1..4096 && bitmap.height in 1..4096 && bitmap.width.toLong()*bitmap.height<=8_000_000)
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val result=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text
        require(result.startsWith("TB1.") && result.length<=2048)
        return result
    }
}
