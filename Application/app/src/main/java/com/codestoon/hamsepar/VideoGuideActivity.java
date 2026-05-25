package com.codestoon.hamsepar;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class VideoGuideActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageView btnClose, btnPlayPause;
    private ProgressBar progressBar;
    private TextView txtError;

    private Handler handler = new Handler();
    private Runnable hidePlayButtonRunnable;
    private File videoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_guide);

        // تنظیم فول اسکرین
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);


        initViews();
        loadAndPlayVideo();
    }

    private void initViews() {
        videoView = findViewById(R.id.videoView);
        btnClose = findViewById(R.id.btnClose);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        progressBar = findViewById(R.id.progressBar);
        txtError = findViewById(R.id.txtError);

        btnClose.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                btnPlayPause.setImageResource(R.drawable.ic_play_circle);
                btnPlayPause.setVisibility(View.VISIBLE);
            } else {
                videoView.start();
                btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                startHideButtonTimer();
            }
        });

        // مخفی کردن دکمه پخش بعد از 3 ثانیه
        hidePlayButtonRunnable = () -> {
            if (videoView.isPlaying()) {
                btnPlayPause.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    btnPlayPause.setVisibility(View.GONE);
                    btnPlayPause.setAlpha(1f);
                });
            }
        };

        // نمایش دکمه پخش با کلیک روی ویدیو
        videoView.setOnClickListener(v -> {
            btnPlayPause.setVisibility(View.VISIBLE);
            btnPlayPause.setAlpha(1f);
            if (videoView.isPlaying()) {
                btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play_circle);
            }
            startHideButtonTimer();
        });
    }

    private void loadAndPlayVideo() {
        progressBar.setVisibility(View.VISIBLE);

        // کپی ویدیو از assets به حافظه موقت
        videoFile = copyVideoFromAssetsToCache("guide_video.mp4");

        if (videoFile != null && videoFile.exists()) {
            Uri videoUri = Uri.fromFile(videoFile);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(mp -> {
                progressBar.setVisibility(View.GONE);
                videoView.start();
                btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                startHideButtonTimer();
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                progressBar.setVisibility(View.GONE);
                txtError.setVisibility(View.VISIBLE);
                btnPlayPause.setVisibility(View.GONE);
                return true;
            });

            videoView.setOnCompletionListener(mp -> {
                btnPlayPause.setImageResource(R.drawable.ic_play_circle);
                btnPlayPause.setVisibility(View.VISIBLE);
            });

        } else {
            progressBar.setVisibility(View.GONE);
            txtError.setVisibility(View.VISIBLE);
        }
    }

    private void startHideButtonTimer() {
        handler.removeCallbacks(hidePlayButtonRunnable);
        handler.postDelayed(hidePlayButtonRunnable, 3000);
    }

    private File copyVideoFromAssetsToCache(String fileName) {
        try {
            InputStream inputStream = getAssets().open(fileName);
            File cacheFile = new File(getCacheDir(), fileName);

            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return cacheFile;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(hidePlayButtonRunnable);
        if (videoView != null && videoView.isPlaying()) {
            videoView.stopPlayback();
        }
        if (videoFile != null && videoFile.exists()) {
            videoFile.delete();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}