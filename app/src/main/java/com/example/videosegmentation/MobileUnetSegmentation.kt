package com.example.videosegmentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

class MobileUnetSegmentation(private val context: MainActivity) {
    private val durationLimit: Long = 6

    fun processURL(url: String?) {
        Log.d(LOGTAG, String.format("Start native segmentation of %s", url))

        val isVideo = true

        if (isVideo) {
            processVideo(url)
            return
        }

        val folderPath = context.filesDir.absolutePath
        val uuid = UUID.randomUUID().toString().substring(0, 5)
        val bodyURL = String.format("%s/feathered_%s.png", folderPath, uuid)
        val fullsizeBodyURL = String.format("%s/cropped_body_%s.png", folderPath, uuid)

        val segmentationHelper = SegmentationHelper(context)

        // body is Just body cropped by alpha
        // fullsizeBody is body on transparent background size of origin
        // cropRect is rectangle coordinates which contains all visible pixels only

        val (body, fullsizeBody, cropRect) = segmentationHelper.segmentation(url!!)


        // Save to file
        try {
            FileOutputStream(bodyURL).use { out ->
                body.compress(Bitmap.CompressFormat.PNG, 100, out)
                Log.d(LOGTAG, String.format("body saved to %s", bodyURL))
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(LOGTAG, String.format("error while saving %s", bodyURL))
        }

        try {
            FileOutputStream(fullsizeBodyURL).use { out ->
                fullsizeBody.compress(Bitmap.CompressFormat.PNG, 100, out)
                Log.d(LOGTAG, String.format("fullsizeBody saved to %s", fullsizeBodyURL))
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(LOGTAG, String.format("error while saving %s", bodyURL))
        }
    }

    private fun processVideo(url: String?) {
        val startTime = System.currentTimeMillis()
        val res = context.resources
        val afd = res.openRawResourceFd(R.raw.girlorig)
        val metaRetriever = MediaMetadataRetriever()
        metaRetriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        val durationString =
            metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val framesInVideoString =
            metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)

        // Milliseconds
        var rawDuration: Long = 0
        if (durationString != null) {
            // Milliseconds
            rawDuration = durationString.toLong()
        }

        // Seconds
        val durationSeconds = rawDuration / 1000
        val originFPS = (framesInVideoString!!.toLong() / durationSeconds).toInt()
        val outputFPS = 30

        // We need max 6 seconds
        val secondsNeeded = Math.min(durationSeconds, durationLimit)
        val framesNeeded = Math.toIntExact(secondsNeeded) * outputFPS
        val frameTimeDelta = 1.0.toFloat() / outputFPS.toFloat() * 1000000

        val segmentationHelper = SegmentationHelper(context)

        val bitmapToVideoEncoder =
            VideoEncoder { outputFile: File? ->
                Log.d(
                    LOGTAG,
                    String.format("Encoding complete!")
                )
            }

        val folderPath = context.filesDir.absolutePath
        val outputideoURL = String.format("%s/temp.mp4", folderPath)
        val wantedResultRect = Rect(0, 0, 1024, 1024)

        val inputVideoURL = String.format("%s/girlOR.MP4", folderPath)

        val bitmapDecoder = VideoDecoder()
        bitmapDecoder.prepareDecoder(File(inputVideoURL));
        bitmapDecoder.startDecoding()

        // Run encoder in background thread
        thread {

            bitmapToVideoEncoder.startEncoding(
                wantedResultRect.width(),
                wantedResultRect.height() * 2,
                File(outputideoURL)
            )
        }

        var currentTimeNeeded: Long = 0

        while (currentTimeNeeded < framesNeeded * frameTimeDelta) {
            Log.e(LOGTAG, String.format("Get frame for time %f", currentTimeNeeded / 1000000.0))

            // Get frame of video
            val getFrameStartTime = System.currentTimeMillis()
            val extractedImage = bitmapDecoder.nextFrame

            val getFrameTime = System.currentTimeMillis() - getFrameStartTime
            Log.d(LOGTAG, String.format("Get frame in %f", getFrameTime.toDouble() / 1000.0))

            if (extractedImage == null) {
                Log.e(
                    LOGTAG,
                    String.format(
                        "MediaMetadataRetriever can not get frame in %f! Try to repeat",
                        getFrameTime.toDouble() / 1000.0
                    )
                )
                continue
            }

            // Go Segmentation
            val segmentationStartTime = System.currentTimeMillis()
            val segmentedBitmap = segmentationHelper.predictImage(extractedImage)
            val segmentationTime = System.currentTimeMillis() - segmentationStartTime

            Log.d(
                LOGTAG,
                String.format(
                    "Total processing for frame: %f seconds",
                    segmentationTime.toDouble() / 1000.0
                )
            )

            val resultBitmap = Bitmap.createBitmap(
                wantedResultRect.width(),
                wantedResultRect.height() * 2,
                segmentedBitmap!!.config
            )

            val canvas = Canvas(resultBitmap)
            val originBitmapRectToDraw = Rect(0, 0, segmentedBitmap.width, segmentedBitmap.height)
            val destRect = Rect(0, 0, resultBitmap.width, resultBitmap.height / 2)
            canvas.drawBitmap(extractedImage, originBitmapRectToDraw, destRect, null)
            val destRect2 =
                Rect(0, resultBitmap.height / 2, resultBitmap.width, resultBitmap.height)
            canvas.drawBitmap(segmentedBitmap, originBitmapRectToDraw, destRect2, null)

            bitmapToVideoEncoder.queueFrame(resultBitmap)

            Log.e(LOGTAG, "--------------")
            currentTimeNeeded += frameTimeDelta.toLong()
        }

        bitmapDecoder.endDecoding()
        bitmapToVideoEncoder.stopEncoding()

        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        Log.e(LOGTAG, String.format("Finish segmentation in %f", elapsedTime))

        Handler(Looper.getMainLooper()).post {
            context.playOrigin(outputideoURL)
            context.playAlphaAbove(outputideoURL)
            //                context.share(videoURL);
        }
    }

    companion object {
        const val LOGTAG = "SEGMENTATION"
    }
}