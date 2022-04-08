package com.example.videosegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.Size
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


data class NormalRect(
    val x: Int,
    val y: Int,
    val height: Int,
    val width: Int
)

fun normalRectToRect(normalRect: NormalRect): Rect {
    val right = normalRect.width + normalRect.x
    val bottom = normalRect.height + normalRect.y

    return Rect(normalRect.x, normalRect.y, right, bottom)
}

data class SegmentationResult(
    // Just body cropped by alpha
    val body: Bitmap,

    // Body on transparent background size of origin
    val fullSizeBody: Bitmap,

    // Rectangle coordinates which contains all visible pixels only
    val cropRect: NormalRect
)

class SegmentationHelper(private val context: Context) {
    private var loadResult = false
    private var ddims = intArrayOf(1, 3, 512, 512)
    private var interpreter: Interpreter? = null
    private var uri: Uri? = null

    companion object {
        private const val TAG = "SegmentationHelper"

        private const val PADDLE_MODEL = "checkpoint_weights_512.0066_1.21"
    }

    init {
        loadModel(PADDLE_MODEL)
    }

    fun segmentation(image_path: String): SegmentationResult {
        if (!TextUtils.isEmpty(image_path)) {
            uri = Uri.parse(image_path)
        }

        // Origin bitmap, but max size limited by 1250x1000. Just decode optimization
        var originBitmapScaled = PhotoUtil.getScaleBitmap(image_path)

        // Fit origin in 1250x1000 bitmap
        originBitmapScaled = PhotoUtil.fitBitmapInSize(originBitmapScaled, Size(1000, 1250))

        // If you want to fit mask in square, use ImageUtils.converterBitmapSquare()
        val originHeight = originBitmapScaled.height
        val originWidth = originBitmapScaled.width

        // SEGMENTATION START:

        // Returns mask on transparent background
        val maskSquared = predictImage(originBitmapScaled)

        // If you want to color non-transparent pixels, use ImageUtils.replaceBitmapColor()

        // White mask on transparent background, but in origin image scale
        val fullSizeMask = ImageUtils.imageScaled(maskSquared!!, originWidth, originHeight)

        // Body on transparent background size of origin
        val fullSizeBody = ImageUtils.applyMask(originBitmapScaled, fullSizeMask)

        // Just body cropped by alpha
        val (body, cropRect) = ImageUtils.cropBitmapByAlpha(fullSizeBody)

        return SegmentationResult(body!!, fullSizeBody, cropRect!!)
    }

    // load infer model
    private fun loadModel(model: String) {
        val compatList = CompatibilityList()

        val options = Interpreter.Options().apply {
            if (compatList.isDelegateSupportedOnThisDevice) {
                // if the device has a supported GPU, add the GPU delegate

                Log.d(TAG, "GPU is available!")
                val delegateOptions = compatList.bestOptionsForThisDevice
                this.addDelegate(GpuDelegate(delegateOptions))
            } else {
                // if the GPU is not supported, run on 6 threads
                Log.d(TAG, "GPU is not available!")
                this.numThreads = 6
            }
        }

        try {
            interpreter = Interpreter(loadModelFile(model), options)
            Log.d(TAG, "$model model load success")

            loadResult = true
        } catch (e: IOException) {
            Log.d(TAG, "$model model load fail")
            loadResult = false
            e.printStackTrace()
        }
    }

    /**
     * Memory-map the model file in Assets.
     */
    @Throws(IOException::class)
    private fun loadModelFile(model: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("$model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    //  predict image
    fun predictImage(originBitmap: Bitmap): Bitmap? {

        val scaleStartTime = System.currentTimeMillis()
        val inputData = PhotoUtil.getScaledMatrix(originBitmap, ddims)
        val scaleTime = (System.currentTimeMillis() - scaleStartTime) / 1000.0
        Log.d(TAG, "Scaled to 512x512 in $scaleTime")

        val bufferCreateStartTime = System.currentTimeMillis()
        val outputData: TensorBuffer =
            TensorBuffer.createFixedSize(intArrayOf(512, 512), DataType.FLOAT32)
        val tensorBufferCreateTime = (System.currentTimeMillis() - bufferCreateStartTime) / 1000.0
        Log.d(TAG, "Created TensorBuffer in $tensorBufferCreateTime")

        try {
            val modelRunStartTime = System.currentTimeMillis()

            // get predict result
            interpreter!!.run(inputData, outputData.buffer)

            val modelRunTime = (System.currentTimeMillis() - modelRunStartTime) / 1000.0
            Log.d(TAG, "Segmentation time: $modelRunTime")

            val bitmapCreatingStuffStart = System.currentTimeMillis()

            val imgData = ByteArray(512 * 512 * 4)
            val outputFloatArray = outputData.floatArray

            for (row in 0 until 512) {
                for (col in 0 until 512) {
                    if (outputFloatArray[col + row * 512] > 0.5) {
                        val idx = col * 4 + row * 4 * 512
                        imgData[idx] = 255.toByte()
                        imgData[idx + 1] = 255.toByte()
                        imgData[idx + 2] = 255.toByte()
                        imgData[idx + 3] = 255.toByte()
                    }
                }
            }

            val stitchBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

            stitchBmp.copyPixelsFromBuffer(ByteBuffer.wrap(imgData))

            val fullsizeMask =
                ImageUtils.imageScaled(stitchBmp, originBitmap.width, originBitmap.height)

            val bitmapStuffTime = (System.currentTimeMillis() - bitmapCreatingStuffStart) / 1000.0
            Log.d(TAG, "Bitmap stuff time: $bitmapStuffTime")

            return fullsizeMask

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

}
