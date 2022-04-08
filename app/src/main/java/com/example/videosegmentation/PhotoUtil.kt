package com.example.videosegmentation

import android.graphics.*
import android.util.Size
import java.nio.ByteBuffer
import java.nio.ByteOrder


object PhotoUtil {

    // TensorFlow model，get predict data
    fun getScaledMatrix(bitmap: Bitmap, ddims: IntArray): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(ddims[0] * ddims[1] * ddims[2] * ddims[3] * 4)
        imgData.order(ByteOrder.nativeOrder())

        // get image pixel
        val pixels = IntArray(ddims[2] * ddims[3])
        val bm = Bitmap.createScaledBitmap(bitmap, ddims[2], ddims[3], true)
        bm.getPixels(pixels, 0, bm.width, 0, 0, ddims[2], ddims[3])

        var pixel = 0

        for (i in 0 until ddims[2]) {
            for (j in 0 until ddims[3]) {
                val `var` = pixels[pixel++]
                val v1 = ((`var` shr 16 and 0xFF) - 144.75f) / 65.5f
                val v2 = ((`var` shr 8 and 0xFF) - 137.707f) / 61.69f
                val v3 = ((`var` and 0xFF) - 129.66f) / 62.33f

                imgData.putFloat(v1)
                imgData.putFloat(v2)
                imgData.putFloat(v3)
            }
        }

        if (bm.isRecycled) {
            bm.recycle()
        }
        return imgData
    }

    fun getScaledMatrix1(ddims: IntArray): ByteBuffer {
        return ByteBuffer.allocateDirect(ddims[0] * ddims[1] * ddims[2] * ddims[3] * 4)
    }

    // compress picture
    fun getScaleBitmap(filePath: String): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, options)

        val bmpWidth = options.outWidth
        val bmpHeight = options.outHeight

        val maxSize = if (bmpWidth > bmpHeight) 1000 else 1250

        // compress picture with inSampleSize
        options.inSampleSize = 1

        while (true) {
            if (bmpWidth / options.inSampleSize < maxSize || bmpHeight / options.inSampleSize < maxSize) {
                break
            }

            options.inSampleSize *= 2
        }

        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(filePath, options)
    }

    fun fitBitmapInSize(bitmap: Bitmap, size: Size): Bitmap {
        val bitmaptemp = Bitmap.createBitmap(size.width, size.height, bitmap.config)
        val canvas = Canvas(bitmaptemp)

        // Part of origin bitmap we want to draw (a whole bitmap)
        val originBitmapRectToDraw = Rect(0, 0, bitmap.width, bitmap.height)

        if (bitmap.width > bitmap.height) {
            // Origin is wider.
            // There will be empty horizontal lines above and below origin on result bitmap

            val resizeFactor = size.width.toDouble() / bitmap.width

            // Destination rect calculations
            val destinationHeight = (bitmap.height * resizeFactor).toInt()
            val destinationY = (size.height - destinationHeight) / 2

            val destRect = Rect(0, destinationY, size.width, destinationY + destinationHeight)

            canvas.drawBitmap(
                bitmap,
                originBitmapRectToDraw,
                destRect,
                null
            )
        } else {
            // Origin is higher.
            // There will be empty vertical lines on the left and the right on result bitmap

            val resizeFactor = size.height.toDouble() / bitmap.height

            // Destination rect calculations
            val destinationWidth = (bitmap.width * resizeFactor).toInt()
            val destinationX = (size.width - destinationWidth) / 2

            val destRect = Rect(destinationX, 0, destinationX + destinationWidth, size.height)

            canvas.drawBitmap(
                bitmap,
                originBitmapRectToDraw,
                destRect,
                null
            )
        }

        return bitmaptemp
    }
}
