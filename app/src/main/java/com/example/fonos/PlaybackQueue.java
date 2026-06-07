package com.example.fonos;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlaybackQueue {
    private static final String EXTRA_BOOK_ID = "playback_book_id";
    private static final String EXTRA_BOOK_TITLE = "playback_book_title";
    private static final String EXTRA_AUTHOR = "playback_author";
    private static final String EXTRA_CHAPTER_IDS = "playback_chapter_ids";
    private static final String EXTRA_CHAPTER_TITLES = "playback_chapter_titles";
    private static final String EXTRA_AUDIO_URLS = "playback_audio_urls";
    private static final String EXTRA_START_INDEX = "playback_start_index";

    private PlaybackQueue() {
    }

    static Intent createPlayerIntent(
            Context context,
            String bookId,
            String bookTitle,
            String author,
            ArrayList<String> chapterIds,
            ArrayList<String> chapterTitles,
            ArrayList<String> audioUrls,
            int startIndex
    ) {
        Intent intent = new Intent(context, AudioPlayer.class);
        intent.putExtra(EXTRA_BOOK_ID, bookId);
        intent.putExtra(EXTRA_BOOK_TITLE, bookTitle);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putStringArrayListExtra(EXTRA_CHAPTER_IDS, chapterIds);
        intent.putStringArrayListExtra(EXTRA_CHAPTER_TITLES, chapterTitles);
        intent.putStringArrayListExtra(EXTRA_AUDIO_URLS, audioUrls);
        intent.putExtra(EXTRA_START_INDEX, startIndex);
        return intent;
    }

    static boolean hasQueue(Intent intent) {
        ArrayList<String> audioUrls = intent.getStringArrayListExtra(EXTRA_AUDIO_URLS);
        return audioUrls != null && !audioUrls.isEmpty();
    }

    static int getStartIndex(Intent intent) {
        ArrayList<String> audioUrls = intent.getStringArrayListExtra(EXTRA_AUDIO_URLS);
        if (audioUrls == null || audioUrls.isEmpty()) {
            return 0;
        }

        int requestedIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);
        return Math.max(0, Math.min(requestedIndex, audioUrls.size() - 1));
    }

    @Nullable
    static String getRequestedMediaId(Intent intent) {
        List<MediaItem> mediaItems = toMediaItems(intent);
        if (mediaItems.isEmpty()) {
            return null;
        }
        return mediaItems.get(getStartIndex(intent)).mediaId;
    }

    static List<MediaItem> toMediaItems(Intent intent) {
        ArrayList<String> audioUrls = intent.getStringArrayListExtra(EXTRA_AUDIO_URLS);
        if (audioUrls == null || audioUrls.isEmpty()) {
            return Collections.emptyList();
        }

        String bookId = valueOrFallback(intent.getStringExtra(EXTRA_BOOK_ID), "book");
        String bookTitle = valueOrFallback(intent.getStringExtra(EXTRA_BOOK_TITLE), "Audiobook");
        String author = valueOrFallback(intent.getStringExtra(EXTRA_AUTHOR), "Unknown author");
        ArrayList<String> chapterIds = intent.getStringArrayListExtra(EXTRA_CHAPTER_IDS);
        ArrayList<String> chapterTitles = intent.getStringArrayListExtra(EXTRA_CHAPTER_TITLES);

        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        for (int index = 0; index < audioUrls.size(); index++) {
            String chapterId = valueAtOrFallback(chapterIds, index, String.valueOf(index + 1));
            String chapterTitle = valueAtOrFallback(chapterTitles, index, "Chapter " + (index + 1));
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(chapterTitle)
                    .setArtist(author)
                    .setAlbumTitle(bookTitle)
                    .build();

            mediaItems.add(new MediaItem.Builder()
                    .setMediaId(bookId + ":" + chapterId)
                    .setUri(audioUrls.get(index))
                    .setMediaMetadata(metadata)
                    .build());
        }
        return mediaItems;
    }

    private static String valueAtOrFallback(
            @Nullable ArrayList<String> values,
            int index,
            String fallback
    ) {
        if (values == null || index >= values.size()) {
            return fallback;
        }
        return valueOrFallback(values.get(index), fallback);
    }

    private static String valueOrFallback(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
