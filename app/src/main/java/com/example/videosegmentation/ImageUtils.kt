package com.example.videosegmentation

import android.graphics.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.graphics.PorterDuff

import android.graphics.PorterDuffXfermode
import android.graphics.Bitmap
import java.time.format.DateTimeFormatter


object ImageUtils {

    fun mergeBitmap(firstBitmap: Bitmap, secondBitmap: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(firstBitmap.width, firstBitmap.height, firstBitmap.config)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(firstBitmap, Matrix(), null)
        canvas.drawBitmap(secondBitmap, 0F, 0F, null)
        return bitmap
    }

    fun applyMask(bitmap: Bitmap, mask: Bitmap): Bitmap {
        val canvas = Canvas()
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        val tempCanvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
        tempCanvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null

        //Draw result after performing masking
        canvas.drawBitmap(result, 0f, 0f, Paint())

        return result
    }

    fun cropBitmapByAlpha(sourceBitmap: Bitmap): Pair<Bitmap?, NormalRect?> {
        var minX = sourceBitmap.width
        var minY = sourceBitmap.height
        var maxX = -1
        var maxY = -1

        for (y in 0 until sourceBitmap.height) {
            for (x in 0 until sourceBitmap.width) {
                val alpha = sourceBitmap.getPixel(x, y) shr 24 and 255
                if (alpha > 0) // pixel is not 100% transparent
                {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            // Bitmap is entirely transparent
            return Pair(null, null)
        }

        val cropRect = NormalRect(minX, minY, maxY - minY, maxX - minX)

        Log.d("ImageUtils", cropRect.toString())

        val croppedBitmap = Bitmap.createBitmap(
            sourceBitmap,
            cropRect.x,
            cropRect.y,
            cropRect.width,
            cropRect.height
        )

        // crop bitmap to non-transparent area and return:
        return Pair(croppedBitmap, cropRect)
    }

    fun converterBitmapSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap

        when (bitmap.width > bitmap.height) {
            true -> {
                val bitmaptemp = Bitmap.createBitmap(bitmap.width, bitmap.width, bitmap.config)
                val canvas = Canvas(bitmaptemp)
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(bitmap, 0F, (bitmap.width - bitmap.height) / 2.toFloat(), null)
                return bitmaptemp
            }
            false -> {
                val bitmaptemp = Bitmap.createBitmap(bitmap.height, bitmap.height, bitmap.config)
                val canvas = Canvas(bitmaptemp)
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(bitmap, (bitmap.height - bitmap.width) / 2.toFloat(), 0F, null)
                return bitmaptemp
            }
        }
    }

    fun imageScaled(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap

        val tempBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        if (tempBitmap.isRecycled) {
            tempBitmap.recycle()
        }

        return tempBitmap
    }

    fun replaceBitmapColor(oldBitmap: Bitmap, newColor: Int): Bitmap {
        val mBitmap = oldBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val mBitmapWidth = mBitmap.width
        val mBitmapHeight = mBitmap.height

        var mArrayColorLengh = mBitmapWidth * mBitmapHeight

        for (i in 0 until mBitmapHeight) {
            for (j in 0 until mBitmapWidth) {
                val color = mBitmap.getPixel(j, i)

                if (color != 0) {
                    mBitmap.setPixel(j, i, newColor)
                }
            }
        }
        return mBitmap
    }

    fun saveImageToGallery(bmp: Bitmap, filePath: File): String? {

        val dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        val fileName = "$dtf.jpg"

        val file = File(filePath, fileName)
        var fos: FileOutputStream? = null

        try {
            fos = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()

            return file.path

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        return null
    }
}