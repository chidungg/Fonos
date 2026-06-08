package com.example.fonos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BookDetails extends AppCompatActivity {
    private TextView btnDownload;
    private DownloadedAudioDatabase downloadedAudioDatabase;
    private boolean downloadReceiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AudioDownloadService.ACTION_DOWNLOAD_STATE.equals(intent.getAction())) {
                return;
            }

            String bookId = intent.getStringExtra(AudioDownloadService.EXTRA_BOOK_ID);
            if (!DemoAudioBook.getBookId().equals(bookId)) {
                return;
            }

            String status = intent.getStringExtra(AudioDownloadService.EXTRA_STATUS);
            int downloadedCount = intent.getIntExtra(AudioDownloadService.EXTRA_DOWNLOADED_COUNT, 0);
            int totalCount = intent.getIntExtra(AudioDownloadService.EXTRA_TOTAL_COUNT, DemoAudioBook.getChapterCount());
            updateDownloadState(status, downloadedCount, totalCount, intent.getStringExtra(AudioDownloadService.EXTRA_ERROR));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_book_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        downloadedAudioDatabase = new DownloadedAudioDatabase(this);
        btnDownload = findViewById(R.id.btnDownload);

        findViewById(R.id.btnPlaySample).setOnClickListener(v -> openPlayer(0));
        btnDownload.setOnClickListener(v -> downloadDemoAudio());
        findViewById(R.id.chapter1).setOnClickListener(v -> openPlayer(0));
        findViewById(R.id.chapter2).setOnClickListener(v -> openPlayer(1));
        findViewById(R.id.chapter3).setOnClickListener(v -> openPlayer(2));

        updateDownloadButtonFromDatabase();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(
                this,
                downloadReceiver,
                new IntentFilter(AudioDownloadService.ACTION_DOWNLOAD_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        downloadReceiverRegistered = true;
        updateDownloadButtonFromDatabase();
    }

    @Override
    protected void onStop() {
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadReceiver);
            downloadReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (downloadedAudioDatabase != null) {
            downloadedAudioDatabase.close();
            downloadedAudioDatabase = null;
        }
        super.onDestroy();
    }

    private void openPlayer(int startChapterIndex) {
        startActivity(DemoAudioBook.createPlayerIntent(this, startChapterIndex));
    }

    private void downloadDemoAudio() {
        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading...");
        AudioDownloadService.startDownload(
                this,
                DemoAudioBook.getBookId(),
                DemoAudioBook.getBookTitle(),
                DemoAudioBook.getAuthor(),
                DemoAudioBook.getChapterIds(),
                DemoAudioBook.getChapterTitles(),
                DemoAudioBook.getAudioUrls()
        );
    }

    private void updateDownloadState(String status, int downloadedCount, int totalCount, String error) {
        if (AudioDownloadService.STATUS_STARTED.equals(status)) {
            btnDownload.setEnabled(false);
            btnDownload.setText("Downloading...");
            return;
        }

        if (AudioDownloadService.STATUS_PROGRESS.equals(status)) {
            btnDownload.setEnabled(false);
            btnDownload.setText(downloadedCount + "/" + totalCount);
            return;
        }

        if (AudioDownloadService.STATUS_COMPLETED.equals(status)) {
            btnDownload.setEnabled(false);
            btnDownload.setText("Downloaded");
            Toast.makeText(this, "Audio saved to SQLite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (AudioDownloadService.STATUS_FAILED.equals(status)) {
            btnDownload.setEnabled(true);
            btnDownload.setText("Download");
            Toast.makeText(this, "Download failed: " + error, Toast.LENGTH_LONG).show();
        }
    }

    private void updateDownloadButtonFromDatabase() {
        if (downloadedAudioDatabase == null || btnDownload == null) {
            return;
        }

        boolean downloaded = downloadedAudioDatabase.isBookFullyDownloaded(
                DemoAudioBook.getBookId(),
                DemoAudioBook.getChapterCount()
        );
        btnDownload.setEnabled(!downloaded);
        btnDownload.setText(downloaded ? "Downloaded" : "Download");
    }
}
