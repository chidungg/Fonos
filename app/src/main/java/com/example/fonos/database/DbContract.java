package com.example.fonos.databases;

public final class DbContract {

    private DbContract() {
    }

    public static final String TABLE_APP_SETTINGS = "app_settings";

    public static final String TABLE_CACHED_AUTHORS = "cached_authors";
    public static final String TABLE_CACHED_BOOKS = "cached_books";
    public static final String TABLE_CACHED_CATEGORIES = "cached_categories";
    public static final String TABLE_CACHED_CHAPTERS = "cached_chapters";
    public static final String TABLE_CACHED_CURATED_LISTS = "cached_curated_lists";
    public static final String TABLE_CACHED_LIBRARY = "cached_library";
    public static final String TABLE_CACHED_NARRATORS = "cached_narrators";
    public static final String TABLE_CACHED_SUBSCRIPTION = "cached_subscription";
    public static final String TABLE_CACHED_USER_PROFILE = "cached_user_profile";

    public static final String TABLE_DOWNLOADED_CHAPTERS = "downloaded_chapters";

    public static final String TABLE_LOCAL_BOOKMARKS = "local_bookmarks";
    public static final String TABLE_LOCAL_NOTES = "local_notes";
    public static final String TABLE_LOCAL_PROGRESS = "local_progress";

    public static final String TABLE_PLAYBACK_QUEUE = "playback_queue";
    public static final String TABLE_RECENT_BOOKS = "recent_books";
    public static final String TABLE_SEARCH_HISTORY = "search_history";
    public static final String TABLE_SYNC_QUEUE = "sync_queue";
}