package com.example.fonos;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.List;
import java.util.Locale;

public class AudioPlayer extends AppCompatActivity implements PlaybackService.PlaybackListener {
    private static final long SEEK_INCREMENT_MS = 10_000L;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            progressHandler.postDelayed(this, 500L);
        }
    };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            serviceBound = true;
            playbackService.addPlaybackListener(AudioPlayer.this);
            loadRequestedQueue();
            updatePlayerUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            playbackService = null;
        }
    };

    private PlaybackService playbackService;
    private boolean serviceBound;
    private SeekBar seekBar;
    private TextView txtBookTitle;
    private TextView txtChapterTitle;
    private TextView txtElapsed;
    private TextView txtDuration;
    private TextView btnPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_audio_player);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtBookTitle = findViewById(R.id.txtBookTitle);
        txtChapterTitle = findViewById(R.id.txtChapterTitle);
        txtElapsed = findViewById(R.id.txtElapsed);
        txtDuration = findViewById(R.id.txtDuration);
        btnPause = findViewById(R.id.btnPause);
        seekBar = findViewById(R.id.seekBar);

        btnPause.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.btnRewind).setOnClickListener(v -> seekBy(-SEEK_INCREMENT_MS));
        findViewById(R.id.btnForward).setOnClickListener(v -> seekBy(SEEK_INCREMENT_MS));
        findViewById(R.id.btnPreviousChapter).setOnClickListener(v -> seekToPreviousChapter());
        findViewById(R.id.btnNextChapter).setOnClickListener(v -> seekToNextChapter());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopPlayback());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    txtElapsed.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playbackService != null) {
                    playbackService.seekTo(seekBar.getProgress());
                }
            }
        });

        requestNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, PlaybackService.class);
        serviceIntent.setAction(PlaybackService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        progressHandler.post(progressUpdater);
    }

    @Override
    protected void onStop() {
        progressHandler.removeCallbacks(progressUpdater);
        if (serviceBound) {
            playbackService.removePlaybackListener(this);
            unbindService(serviceConnection);
            playbackService = null;
            serviceBound = false;
        }
        super.onStop();
    }

    private void loadRequestedQueue() {
        if (playbackService == null || !PlaybackQueue.hasQueue(getIntent())) {
            return;
        }

        List<MediaItem> mediaItems = PlaybackQueue.toMediaItems(getIntent());
        playbackService.setQueue(mediaItems, PlaybackQueue.getStartIndex(getIntent()));
    }

    private void togglePlayPause() {
        if (playbackService != null) {
            playbackService.togglePlayPause();
        }
    }

    private void seekBy(long deltaMs) {
        if (playbackService != null) {
            playbackService.seekBy(deltaMs);
        }
    }

    private void seekToPreviousChapter() {
        if (playbackService != null) {
            playbackService.seekToPreviousChapter();
        }
    }

    private void seekToNextChapter() {
        if (playbackService != null) {
            playbackService.seekToNextChapter();
        }
    }

    private void stopPlayback() {
        if (playbackService != null) {
            playbackService.stopPlayback();
        }
        finish();
    }

    private void updatePlayerUi() {
        if (playbackService == null) {
            return;
        }

        MediaMetadata metadata = playbackService.getMediaMetadata();
        txtBookTitle.setText(textOrFallback(metadata.albumTitle, "Audiobook"));
        txtChapterTitle.setText(textOrFallback(metadata.title, "Chapter"));
        btnPause.setText(playbackService.isPlaying() ? "Pause" : "Play");
        findViewById(R.id.btnPreviousChapter).setEnabled(playbackService.hasPreviousChapter());
        findViewById(R.id.btnNextChapter).setEnabled(playbackService.hasNextChapter());
        updateProgress();
    }

    private void updateProgress() {
        if (playbackService == null) {
            return;
        }

        long position = Math.max(0L, playbackService.getCurrentPosition());
        long duration = Math.max(0L, playbackService.getDuration());
        seekBar.setMax((int) Math.min(Integer.MAX_VALUE, Math.max(1L, duration)));
        seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, Math.min(position, duration)));
        txtElapsed.setText(formatTime(position));
        txtDuration.setText(formatTime(duration));
    }

    private String formatTime(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private CharSequence textOrFallback(@Nullable CharSequence value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onPlaybackChanged() {
        updatePlayerUi();
    }

    @Override
    public void onPlaybackError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
