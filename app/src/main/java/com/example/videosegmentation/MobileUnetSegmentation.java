package com.example.videosegmentation;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class MobileUnetSegmentation {
    public static final String LOGTAG = "SEGMENTATION";
    private final MainActivity context;

    public MobileUnetSegmentation(MainActivity context) {
        this.context = context;
    }

    public void processURL(String url) {
        Log.d(LOGTAG, String.format("Start native segmentation of %s", url));

        boolean isVideo = true;

        if (isVideo) {
            processVideo(url);
            return;
        }

        String folderPath = context.getFilesDir().getAbsolutePath();
        String uuid = UUID.randomUUID().toString().substring(0, 5);

        String bodyURL = String.format("%s/feathered_%s.png", folderPath, uuid);
        String fullsizeBodyURL = String.format("%s/cropped_body_%s.png", folderPath, uuid);

        SegmentationHelper segmentationHelper = new SegmentationHelper(context);
        SegmentationResult results = segmentationHelper.segmentation(url);

        // Just body cropped by alpha
        Bitmap body = results.getBody();

        // Body on transparent background size of origin
        Bitmap fullsizeBody = results.getFullSizeBody();

        // Rectangle coordinates which contains all visible pixels only
        NormalRect cropRect = results.getCropRect();

        // Save to file
        try (FileOutputStream out = new FileOutputStream(bodyURL)) {
            body.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d(LOGTAG, String.format("body saved to %s", bodyURL));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOGTAG, String.format("error while saving %s", bodyURL));
        }

        try (FileOutputStream out = new FileOutputStream(fullsizeBodyURL)) {
            fullsizeBody.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d(LOGTAG, String.format("fullsizeBody saved to %s", fullsizeBodyURL));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOGTAG, String.format("error while saving %s", bodyURL));
        }
    }

    public void processVideo(String url) {
        long startTime = System.currentTimeMillis();

        Resources res = context.getResources();
        AssetFileDescriptor afd = res.openRawResourceFd(R.raw.girlorig);

        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        metaRetriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

        String durationString = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String framesInVideoString = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);

        // Milliseconds
        long rawDuration = 0;

        if (durationString != null) {
            // Milliseconds
            rawDuration = Long.parseLong(durationString);
        }

        // Seconds
        long durationSeconds = rawDuration / 1000;
        int originFPS = (int)(Long.parseLong(framesInVideoString) / durationSeconds);
        int outputFPS = 30;

        // We need max 6 seconds
        long durationLimit = 6;
        long secondsNeeded = Math.min(durationSeconds, durationLimit);
        int framesNeeded = Math.toIntExact(secondsNeeded) * outputFPS;

        float frameTimeDelta = ((float) 1.0 / (float) outputFPS) * 1000000;

        SegmentationHelper segmentationHelper = new SegmentationHelper(context);
        BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(outputFile -> Log.d(LOGTAG, String.format("Encoding complete!")));

        String folderPath = context.getFilesDir().getAbsolutePath();
        String videoURL = String.format("%s/temp.mp4", folderPath);

        Rect wantedResultRect = new Rect(0, 0, 1024, 1024);

        // Run encoder in background thread
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                bitmapToVideoEncoder.startEncoding(wantedResultRect.width(), wantedResultRect.height() * 2, new File(videoURL));
            }
        });

        long currentTimeNeeded = 0;
        boolean isFirst = true;

        while (currentTimeNeeded < framesNeeded * frameTimeDelta) {
            Log.e(LOGTAG, String.format("Get frame for time %f", currentTimeNeeded / 1000000.0d));

            // Get frame of video
            long getFrameStartTime = System.currentTimeMillis();
            Bitmap extractedImage = null;

            if (isFirst) {
                extractedImage = metaRetriever.getFrameAtIndex(3);
            } else {
                extractedImage = metaRetriever.getFrameAtTime(currentTimeNeeded, MediaMetadataRetriever.OPTION_CLOSEST);
            }

            long getFrameTime = (System.currentTimeMillis() - getFrameStartTime);
            Log.d(LOGTAG, String.format("Get frame in %f", (double) getFrameTime / 1000.0d));

            if (extractedImage == null) {
                Log.e(LOGTAG, String.format("MediaMetadataRetriever can not get frame in %f! Try to repeat", (double) getFrameTime / 1000.0d));
                continue;
            }

            // Go Segmentation
            long segmentationStartTime = System.currentTimeMillis();
            Bitmap segmentedBitmap = segmentationHelper.predictImage(extractedImage);
            long segmentationTime = (System.currentTimeMillis() - segmentationStartTime);
            Log.d(LOGTAG, String.format("Total processing for frame: %f seconds", (double) segmentationTime / 1000.0d));

            Bitmap resultBitmap = Bitmap.createBitmap(wantedResultRect.width(), wantedResultRect.height() * 2, segmentedBitmap.getConfig());
            Canvas canvas = new Canvas(resultBitmap);

            Rect originBitmapRectToDraw = new Rect(0, 0, segmentedBitmap.getWidth(), segmentedBitmap.getHeight());
            Rect destRect = new Rect(0, 0, resultBitmap.getWidth(), resultBitmap.getHeight() / 2);
            canvas.drawBitmap(extractedImage, originBitmapRectToDraw, destRect, null);

            Rect destRect2 = new Rect(0, resultBitmap.getHeight() / 2, resultBitmap.getWidth(), resultBitmap.getHeight());
            canvas.drawBitmap(segmentedBitmap, originBitmapRectToDraw, destRect2, null);

            bitmapToVideoEncoder.queueFrame(resultBitmap);
            Log.e(LOGTAG, "--------------");

            currentTimeNeeded += frameTimeDelta;
            isFirst = false;
        }

        bitmapToVideoEncoder.stopEncoding();
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        Log.e(LOGTAG, String.format("Finish segmentation in %f", elapsedTime / 1000.0d));

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                context.play(videoURL);
//                context.share(videoURL);
            }
        });

    }
}