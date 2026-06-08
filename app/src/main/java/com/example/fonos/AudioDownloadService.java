package com.example.fonos;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioDownloadService extends Service {
    static final String ACTION_DOWNLOAD_STATE = "com.example.fonos.DOWNLOAD_STATE";
    static final String EXTRA_STATUS = "download_status";
    static final String EXTRA_BOOK_ID = "download_book_id";
    static final String EXTRA_DOWNLOADED_COUNT = "downloaded_count";
    static final String EXTRA_TOTAL_COUNT = "total_count";
    static final String EXTRA_ERROR = "download_error";

    static final String STATUS_STARTED = "started";
    static final String STATUS_PROGRESS = "progress";
    static final String STATUS_COMPLETED = "completed";
    static final String STATUS_FAILED = "failed";

    private static final String ACTION_DOWNLOAD_BOOK = "com.example.fonos.DOWNLOAD_BOOK";
    private static final String EXTRA_BOOK_TITLE = "download_book_title";
    private static final String EXTRA_AUTHOR = "download_author";
    private static final String EXTRA_CHAPTER_IDS = "download_chapter_ids";
    private static final String EXTRA_CHAPTER_TITLES = "download_chapter_titles";
    private static final String EXTRA_AUDIO_URLS = "download_audio_urls";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean downloadRunning;

    static void startDownload(
            Context context,
            String bookId,
            String bookTitle,
            String author,
            ArrayList<String> chapterIds,
            ArrayList<String> chapterTitles,
            ArrayList<String> audioUrls
    ) {
        Intent intent = new Intent(context, AudioDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD_BOOK);
        intent.putExtra(EXTRA_BOOK_ID, bookId);
        intent.putExtra(EXTRA_BOOK_TITLE, bookTitle);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putStringArrayListExtra(EXTRA_CHAPTER_IDS, chapterIds);
        intent.putStringArrayListExtra(EXTRA_CHAPTER_TITLES, chapterTitles);
        intent.putStringArrayListExtra(EXTRA_AUDIO_URLS, audioUrls);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_DOWNLOAD_BOOK.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (downloadRunning) {
            return START_NOT_STICKY;
        }

        downloadRunning = true;
        executorService.execute(() -> downloadBook(intent, startId));
        return START_NOT_STICKY;
    }

    private void downloadBook(Intent intent, int startId) {
        String bookId = valueOrFallback(intent.getStringExtra(EXTRA_BOOK_ID), "book");
        String bookTitle = valueOrFallback(intent.getStringExtra(EXTRA_BOOK_TITLE), "Audiobook");
        String author = valueOrFallback(intent.getStringExtra(EXTRA_AUTHOR), "Unknown author");
        ArrayList<String> chapterIds = intent.getStringArrayListExtra(EXTRA_CHAPTER_IDS);
        ArrayList<String> chapterTitles = intent.getStringArrayListExtra(EXTRA_CHAPTER_TITLES);
        ArrayList<String> audioUrls = intent.getStringArrayListExtra(EXTRA_AUDIO_URLS);

        int totalCount = audioUrls == null ? 0 : audioUrls.size();
        broadcastState(STATUS_STARTED, bookId, 0, totalCount, null);

        try (DownloadedAudioDatabase database = new DownloadedAudioDatabase(this)) {
            if (audioUrls == null || audioUrls.isEmpty()) {
                throw new IOException("No audio URL to download");
            }

            String uid = database.uidOrGuest();
            for (int index = 0; index < audioUrls.size(); index++) {
                String audioUrl = audioUrls.get(index);
                String chapterId = valueAtOrFallback(chapterIds, index, String.valueOf(index + 1));
                String chapterTitle = valueAtOrFallback(chapterTitles, index, "Chapter " + (index + 1));
                DownloadResult result = downloadBytes(audioUrl);
                File localAudioFile = writeAudioFile(bookId, chapterId, result.bytes);
                database.saveChapter(new DownloadedAudioDatabase.DownloadedChapter(
                        uid,
                        bookId,
                        chapterId,
                        bookTitle,
                        chapterTitle,
                        audioUrl,
                        localAudioFile.getAbsolutePath(),
                        localAudioFile.length()
                ));
                broadcastState(STATUS_PROGRESS, bookId, index + 1, totalCount, null);
            }

            broadcastState(STATUS_COMPLETED, bookId, totalCount, totalCount, null);
        } catch (Exception error) {
            broadcastState(STATUS_FAILED, bookId, 0, totalCount, error.getMessage());
        } finally {
            downloadRunning = false;
            stopSelf();
        }
    }

    private File writeAudioFile(String bookId, String chapterId, byte[] bytes) throws IOException {
        File audioDirectory = new File(
                getFilesDir(),
                "downloads/audio/" + sanitizeFileName(bookId)
        );
        if (!audioDirectory.exists() && !audioDirectory.mkdirs()) {
            throw new IOException("Cannot create audio download directory");
        }

        File audioFile = new File(audioDirectory, sanitizeFileName(chapterId) + ".mp3");
        try (FileOutputStream outputStream = new FileOutputStream(audioFile)) {
            outputStream.write(bytes);
        }
        return audioFile;
    }

    private String sanitizeFileName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isEmpty() ? "audio" : sanitized;
    }

    private DownloadResult downloadBytes(String audioUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(audioUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);

        int responseCode = connection.getResponseCode();
        if (responseCode < HttpURLConnection.HTTP_OK
                || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            connection.disconnect();
            throw new IOException("Download failed with HTTP " + responseCode);
        }

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return new DownloadResult(connection.getContentType(), outputStream.toByteArray());
        } finally {
            connection.disconnect();
        }
    }

    private void broadcastState(
            String status,
            String bookId,
            int downloadedCount,
            int totalCount,
            @Nullable String error
    ) {
        Intent stateIntent = new Intent(ACTION_DOWNLOAD_STATE);
        stateIntent.setPackage(getPackageName());
        stateIntent.putExtra(EXTRA_STATUS, status);
        stateIntent.putExtra(EXTRA_BOOK_ID, bookId);
        stateIntent.putExtra(EXTRA_DOWNLOADED_COUNT, downloadedCount);
        stateIntent.putExtra(EXTRA_TOTAL_COUNT, totalCount);
        stateIntent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(stateIntent);
    }

    private String valueAtOrFallback(
            @Nullable ArrayList<String> values,
            int index,
            String fallback
    ) {
        if (values == null || index >= values.size()) {
            return fallback;
        }
        return valueOrFallback(values.get(index), fallback);
    }

    private String valueOrFallback(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

    private static final class DownloadResult {
        final String contentType;
        final byte[] bytes;

        DownloadResult(String contentType, byte[] bytes) {
            this.contentType = contentType;
            this.bytes = bytes;
        }
    }
}
