package com.example.fonos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends Service implements Player.Listener {
    static final String ACTION_START = "com.example.fonos.action.START_PLAYBACK_SERVICE";
    private static final String ACTION_TOGGLE_PLAYBACK = "com.example.fonos.action.TOGGLE_PLAYBACK";
    private static final String ACTION_PREVIOUS_CHAPTER = "com.example.fonos.action.PREVIOUS_CHAPTER";
    private static final String ACTION_NEXT_CHAPTER = "com.example.fonos.action.NEXT_CHAPTER";
    private static final String ACTION_STOP = "com.example.fonos.action.STOP_PLAYBACK";
    private static final String CHANNEL_ID = "fonos_audio_playback";
    private static final int NOTIFICATION_ID = 10;

    private final IBinder binder = new LocalBinder();
    private final List<PlaybackListener> listeners = new ArrayList<>();
    private ExoPlayer player;
    private boolean foregroundStarted;

    public interface PlaybackListener {
        void onPlaybackChanged();

        void onPlaybackError(String message);
    }

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(this);
        startPlaybackForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
            togglePlayPause();
        } else if (ACTION_PREVIOUS_CHAPTER.equals(action)) {
            seekToPreviousChapter();
        } else if (ACTION_NEXT_CHAPTER.equals(action)) {
            seekToNextChapter();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else {
            startPlaybackForeground();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        listeners.clear();
        if (player != null) {
            player.removeListener(this);
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    void addPlaybackListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    void setQueue(List<MediaItem> mediaItems, int startIndex) {
        if (player == null || mediaItems.isEmpty()) {
            return;
        }

        int clampedIndex = Math.max(0, Math.min(startIndex, mediaItems.size() - 1));
        MediaItem requestedItem = mediaItems.get(clampedIndex);
        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaId.equals(requestedItem.mediaId)) {
            return;
        }

        player.setMediaItems(mediaItems, clampedIndex, 0L);
        player.prepare();
        player.play();
        notifyPlaybackChanged();
        updateNotification();
    }

    void togglePlayPause() {
        if (player == null) {
            return;
        }

        if (player.isPlaying()) {
            player.pause();
        } else {
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
            player.play();
        }
        notifyPlaybackChanged();
        updateNotification();
    }

    void seekBy(long deltaMs) {
        if (player == null) {
            return;
        }

        long targetPosition = Math.max(0L, player.getCurrentPosition() + deltaMs);
        long duration = player.getDuration();
        if (duration > 0L && duration != C.TIME_UNSET) {
            targetPosition = Math.min(duration, targetPosition);
        }
        player.seekTo(targetPosition);
    }

    void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(Math.max(0L, positionMs));
        }
    }

    void seekToPreviousChapter() {
        if (player != null && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        }
    }

    void seekToNextChapter() {
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        }
    }

    void stopPlayback() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        notifyPlaybackChanged();
        stopForegroundService();
    }

    boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    int getPlaybackState() {
        return player == null ? Player.STATE_IDLE : player.getPlaybackState();
    }

    long getCurrentPosition() {
        return player == null ? 0L : player.getCurrentPosition();
    }

    long getDuration() {
        if (player == null) {
            return 0L;
        }
        long duration = player.getDuration();
        return duration == C.TIME_UNSET ? 0L : duration;
    }

    MediaMetadata getMediaMetadata() {
        return player == null ? MediaMetadata.EMPTY : player.getMediaMetadata();
    }

    boolean hasPreviousChapter() {
        return player != null && player.hasPreviousMediaItem();
    }

    boolean hasNextChapter() {
        return player != null && player.hasNextMediaItem();
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
        notifyPlaybackChanged();
        updateNotification();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        for (PlaybackListener listener : new ArrayList<>(listeners)) {
            listener.onPlaybackError("Cannot play this audio file");
        }
        updateNotification();
    }

    private void startPlaybackForeground() {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification());
            foregroundStarted = true;
        } else {
            updateNotification();
        }
    }

    private void stopForegroundService() {
        foregroundStarted = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void updateNotification() {
        if (foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        MediaMetadata metadata = getMediaMetadata();
        String title = textOrFallback(metadata.albumTitle, "Fonos audio");
        String text = textOrFallback(metadata.title, isPlaying() ? "Playing" : "Ready");
        String playPauseTitle = isPlaying() ? "Pause" : "Play";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(createActivityPendingIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying())
                .addAction(0, "Previous", createServicePendingIntent(ACTION_PREVIOUS_CHAPTER, 1))
                .addAction(0, playPauseTitle, createServicePendingIntent(ACTION_TOGGLE_PLAYBACK, 2))
                .addAction(0, "Next", createServicePendingIntent(ACTION_NEXT_CHAPTER, 3))
                .addAction(0, "Stop", createServicePendingIntent(ACTION_STOP, 4))
                .build();
    }

    private PendingIntent createActivityPendingIntent() {
        Intent intent = new Intent(this, AudioPlayer.class);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createServicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Audio playback",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Controls audio playback while the app is running in the background");
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }

    private void notifyPlaybackChanged() {
        for (PlaybackListener listener : new ArrayList<>(listeners)) {
            listener.onPlaybackChanged();
        }
    }

    private String textOrFallback(@Nullable CharSequence value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value.toString();
    }
}
