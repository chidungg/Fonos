package com.example.fonos;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class AudioPlayer extends AppCompatActivity implements Player.Listener {
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

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
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
                if (controller != null) {
                    controller.seekTo(seekBar.getProgress());
                }
            }
        });

        requestNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(
                this,
                new ComponentName(this, PlaybackService.class)
        );
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(
                this::onControllerConnected,
                ContextCompat.getMainExecutor(this)
        );
        progressHandler.post(progressUpdater);
    }

    @Override
    protected void onStop() {
        progressHandler.removeCallbacks(progressUpdater);
        if (controller != null) {
            controller.removeListener(this);
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        super.onStop();
    }

    private void onControllerConnected() {
        if (controllerFuture == null) {
            return;
        }

        try {
            controller = controllerFuture.get();
            controller.addListener(this);
            loadRequestedQueue();
            updatePlayerUi();
        } catch (ExecutionException error) {
            Toast.makeText(this, "Cannot connect to audio service", Toast.LENGTH_LONG).show();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadRequestedQueue() {
        if (controller == null || !PlaybackQueue.hasQueue(getIntent())) {
            return;
        }

        String requestedMediaId = PlaybackQueue.getRequestedMediaId(getIntent());
        MediaItem currentMediaItem = controller.getCurrentMediaItem();
        if (currentMediaItem != null && currentMediaItem.mediaId.equals(requestedMediaId)) {
            return;
        }

        List<MediaItem> mediaItems = PlaybackQueue.toMediaItems(getIntent());
        controller.setMediaItems(mediaItems, PlaybackQueue.getStartIndex(getIntent()), 0L);
        controller.prepare();
        controller.play();
    }

    private void togglePlayPause() {
        if (controller == null) {
            return;
        }

        if (controller.isPlaying()) {
            controller.pause();
        } else {
            if (controller.getPlaybackState() == Player.STATE_IDLE) {
                controller.prepare();
            }
            controller.play();
        }
    }

    private void seekBy(long deltaMs) {
        if (controller == null) {
            return;
        }

        long targetPosition = Math.max(0L, controller.getCurrentPosition() + deltaMs);
        long duration = controller.getDuration();
        if (duration > 0L) {
            targetPosition = Math.min(duration, targetPosition);
        }
        controller.seekTo(targetPosition);
    }

    private void seekToPreviousChapter() {
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem();
        }
    }

    private void seekToNextChapter() {
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem();
        }
    }

    private void stopPlayback() {
        if (controller != null) {
            controller.stop();
            controller.clearMediaItems();
        }
        finish();
    }

    private void updatePlayerUi() {
        if (controller == null) {
            return;
        }

        MediaMetadata metadata = controller.getMediaMetadata();
        txtBookTitle.setText(textOrFallback(metadata.albumTitle, "Audiobook"));
        txtChapterTitle.setText(textOrFallback(metadata.title, "Chapter"));
        btnPause.setText(controller.isPlaying() ? "Pause" : "Play");
        findViewById(R.id.btnPreviousChapter).setEnabled(controller.hasPreviousMediaItem());
        findViewById(R.id.btnNextChapter).setEnabled(controller.hasNextMediaItem());
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null) {
            return;
        }

        long position = Math.max(0L, controller.getCurrentPosition());
        long duration = Math.max(0L, controller.getDuration());
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
    public void onEvents(Player player, Player.Events events) {
        updatePlayerUi();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Toast.makeText(this, "Cannot play this audio file", Toast.LENGTH_LONG).show();
    }
}
