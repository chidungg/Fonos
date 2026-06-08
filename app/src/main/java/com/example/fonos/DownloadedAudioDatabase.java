package com.example.fonos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class DownloadedAudioDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "dbfonos.db";
    private static final int DATABASE_VERSION = 1;

    static final String TABLE_CHAPTERS = "downloaded_chapters";
    private static final String COLUMN_DOWNLOAD_ID = "download_id";
    private static final String COLUMN_UID = "uid";
    private static final String COLUMN_BOOK_ID = "book_id";
    private static final String COLUMN_CHAPTER_ID = "chapter_id";
    private static final String COLUMN_BOOK_TITLE = "book_title";
    private static final String COLUMN_CHAPTER_TITLE = "chapter_title";
    private static final String COLUMN_AUDIO_URL = "audio_url";
    private static final String COLUMN_LOCAL_AUDIO_PATH = "local_audio_path";
    private static final String COLUMN_DOWNLOAD_STATUS = "download_status";
    private static final String COLUMN_DOWNLOAD_PROGRESS = "download_progress";
    private static final String COLUMN_FILE_SIZE = "file_size";
    private static final String COLUMN_DOWNLOADED_AT = "downloaded_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String STATUS_COMPLETED = "completed";

    private final Context appContext;

    DownloadedAudioDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
        copyPrebuiltDatabaseIfMissing();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureDownloadedChaptersTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureDownloadedChaptersTable(db);
    }

    void saveChapter(DownloadedChapter chapter) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DOWNLOAD_ID, chapter.downloadId);
        values.put(COLUMN_UID, chapter.uid);
        values.put(COLUMN_BOOK_ID, chapter.bookId);
        values.put(COLUMN_CHAPTER_ID, chapter.chapterId);
        values.put(COLUMN_BOOK_TITLE, chapter.bookTitle);
        values.put(COLUMN_CHAPTER_TITLE, chapter.chapterTitle);
        values.put(COLUMN_AUDIO_URL, chapter.audioUrl);
        values.put(COLUMN_LOCAL_AUDIO_PATH, chapter.localAudioPath);
        values.put(COLUMN_DOWNLOAD_STATUS, STATUS_COMPLETED);
        values.put(COLUMN_DOWNLOAD_PROGRESS, 100);
        values.put(COLUMN_FILE_SIZE, chapter.fileSize);
        values.put(COLUMN_DOWNLOADED_AT, now);
        values.put(COLUMN_UPDATED_AT, now);

        getWritableDatabase().insertWithOnConflict(
                TABLE_CHAPTERS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    boolean isBookFullyDownloaded(String bookId, int expectedChapterCount) {
        if (expectedChapterCount <= 0) {
            return false;
        }
        return getDownloadedChapterCount(bookId) >= expectedChapterCount;
    }

    int getDownloadedChapterCount(String bookId) {
        String uid = uidOrGuest();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_CHAPTERS
                        + " WHERE " + COLUMN_UID + " = ?"
                        + " AND " + COLUMN_BOOK_ID + " = ?"
                        + " AND " + COLUMN_DOWNLOAD_STATUS + " = ?",
                new String[]{uid, bookId, STATUS_COMPLETED}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    String uidOrGuest() {
        String uid = SessionManager.getUid(appContext);
        return uid == null || uid.trim().isEmpty() ? "guest" : uid;
    }

    private void ensureDownloadedChaptersTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_CHAPTERS + " ("
                        + COLUMN_DOWNLOAD_ID + " TEXT PRIMARY KEY, "
                        + COLUMN_UID + " TEXT, "
                        + COLUMN_BOOK_ID + " TEXT, "
                        + COLUMN_CHAPTER_ID + " TEXT, "
                        + COLUMN_BOOK_TITLE + " TEXT, "
                        + COLUMN_CHAPTER_TITLE + " TEXT, "
                        + COLUMN_AUDIO_URL + " TEXT, "
                        + COLUMN_LOCAL_AUDIO_PATH + " TEXT, "
                        + "ebook_url TEXT, "
                        + "local_ebook_path TEXT, "
                        + COLUMN_DOWNLOAD_STATUS + " TEXT, "
                        + COLUMN_DOWNLOAD_PROGRESS + " INTEGER DEFAULT 0, "
                        + COLUMN_FILE_SIZE + " INTEGER DEFAULT 0, "
                        + COLUMN_DOWNLOADED_AT + " INTEGER, "
                        + COLUMN_UPDATED_AT + " INTEGER"
                        + ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloaded_chapters_uid ON "
                + TABLE_CHAPTERS + " (" + COLUMN_UID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloaded_chapters_book ON "
                + TABLE_CHAPTERS + " (" + COLUMN_BOOK_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloaded_chapters_status ON "
                + TABLE_CHAPTERS + " (" + COLUMN_DOWNLOAD_STATUS + ")");
    }

    private void copyPrebuiltDatabaseIfMissing() {
        File databaseFile = appContext.getDatabasePath(DATABASE_NAME);
        if (databaseFile.exists()) {
            return;
        }

        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }

        try (InputStream inputStream = appContext.getAssets().open(DATABASE_NAME);
             FileOutputStream outputStream = new FileOutputStream(databaseFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException ignored) {
            // If the asset is absent, SQLiteOpenHelper will create the table on first open.
        }
    }

    static final class DownloadedChapter {
        final String downloadId;
        final String uid;
        final String bookId;
        final String chapterId;
        final String bookTitle;
        final String chapterTitle;
        final String audioUrl;
        final String localAudioPath;
        final long fileSize;

        DownloadedChapter(
                String uid,
                String bookId,
                String chapterId,
                String bookTitle,
                String chapterTitle,
                String audioUrl,
                String localAudioPath,
                long fileSize
        ) {
            this.downloadId = uid + ":" + bookId + ":" + chapterId;
            this.uid = uid;
            this.bookId = bookId;
            this.chapterId = chapterId;
            this.bookTitle = bookTitle;
            this.chapterTitle = chapterTitle;
            this.audioUrl = audioUrl;
            this.localAudioPath = localAudioPath;
            this.fileSize = fileSize;
        }
    }
}
