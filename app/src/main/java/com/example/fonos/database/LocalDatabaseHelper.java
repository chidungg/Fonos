package com.example.fonos.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LocalDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "SQLiteTest";

    private static final String DATABASE_NAME = "fonoslocal.db";
    private static final int DATABASE_VERSION = 1;

    // Đường dẫn này tính từ thư mục assets
    private static final String ASSET_DATABASE_PATH = "database/" + DATABASE_NAME;

    private final Context context;

    public LocalDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Không tạo bảng ở đây vì DB đã có sẵn trong assets.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Chưa cần xử lý migration ở giai đoạn này.
    }

    @Override
    public synchronized SQLiteDatabase getReadableDatabase() {
        copyDatabaseIfNeeded();
        return super.getReadableDatabase();
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        copyDatabaseIfNeeded();
        return super.getWritableDatabase();
    }

    private void copyDatabaseIfNeeded() {
        File dbFile = context.getDatabasePath(DATABASE_NAME);

        Log.d(TAG, "Internal DB path: " + dbFile.getAbsolutePath());
        Log.d(TAG, "Asset DB path: " + ASSET_DATABASE_PATH);

        if (dbFile.exists()) {
            Log.d(TAG, "Database already exists, skip copy");
            return;
        }

        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try {
            InputStream inputStream = context.getAssets().open(ASSET_DATABASE_PATH);
            FileOutputStream outputStream = new FileOutputStream(dbFile);

            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            Log.d(TAG, "Database copied successfully");

        } catch (IOException e) {
            Log.e(TAG, "Cannot copy database from assets. Expected path: " + ASSET_DATABASE_PATH, e);
            throw new RuntimeException("Không thể copy database từ assets", e);
        }
    }
}