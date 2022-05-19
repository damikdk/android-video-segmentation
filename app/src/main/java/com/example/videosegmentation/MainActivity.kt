package com.example.videosegmentation

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.daasuu.epf.EPlayerView
import com.daasuu.epf.filter.AlphaFrameFilter
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import java.io.File
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Handler(Looper.getMainLooper()).postDelayed({
            AsyncTask.execute {
                MobileUnetSegmentation(this).processURL("")
            }

            // Uncomment if you already have temp file and you don't need Segmentation
//            val videoURL = String.format("%s/temp.mp4", filesDir.absolutePath)
//            playOrigin(videoURL)
//            playAlphaAbove(videoURL)
        }, 200)
    }

    fun share(url: String) {
        // It's kind of broken
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "video/mp4"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(url)))
        intent.setDataAndType(Uri.parse(url), "video/mp4")
        val chooserIntent = Intent.createChooser(intent, "Share")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooserIntent)
    }

    fun playAlphaAbove(url: String) {
        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, java.lang.String.valueOf(R.string.app_name))
        )

        // This is the MediaSource representing the media to be played.
        val videoSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.fromFile(File(url)))

        // SimpleExoPlayer
        val player = ExoPlayerFactory.newSimpleInstance(this)
        player.prepare(videoSource)
        player.playWhenReady = true
        player.repeatMode = Player.REPEAT_MODE_ALL

        val ePlayerView = EPlayerView(this)

        // set SimpleExoPlayer
        ePlayerView.setSimpleExoPlayer(player);
        ePlayerView.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addContentView(ePlayerView, ePlayerView.layoutParams)
        ePlayerView.onResume()

        ePlayerView.setGlFilter(AlphaFrameFilter())
    }

    fun playOrigin(url: String) {
        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, java.lang.String.valueOf(R.string.app_name))
        )

        // This is the MediaSource representing the media to be played.
        val videoSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.fromFile(File(url)))

        // SimpleExoPlayer
        val player2 = ExoPlayerFactory.newSimpleInstance(this)
        player2.prepare(videoSource)
        player2.playWhenReady = true
        player2.repeatMode = Player.REPEAT_MODE_ALL

        val ePlayerView2 = EPlayerView(this)

        // set SimpleExoPlayer
        ePlayerView2.setSimpleExoPlayer(player2);
        ePlayerView2.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addContentView(ePlayerView2, ePlayerView2.layoutParams)
        ePlayerView2.onResume()
    }
}