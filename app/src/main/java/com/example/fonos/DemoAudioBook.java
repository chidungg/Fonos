package com.example.fonos;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Arrays;

final class DemoAudioBook {
    private static final String BOOK_ID = "demo-book";
    private static final String BOOK_TITLE = "Demo Audiobook";
    private static final String AUTHOR = "Fonos";

    private DemoAudioBook() {
    }

    static Intent createPlayerIntent(Context context, int startChapterIndex) {
        return PlaybackQueue.createPlayerIntent(
                context,
                BOOK_ID,
                BOOK_TITLE,
                AUTHOR,
                getChapterIds(),
                getChapterTitles(),
                getAudioUrls(),
                startChapterIndex
        );
    }

    static String getBookId() {
        return BOOK_ID;
    }

    static String getBookTitle() {
        return BOOK_TITLE;
    }

    static String getAuthor() {
        return AUTHOR;
    }

    static int getChapterCount() {
        return getAudioUrls().size();
    }

    static ArrayList<String> getChapterIds() {
        return new ArrayList<>(Arrays.asList("chapter-1", "chapter-2", "chapter-3"));
    }

    static ArrayList<String> getChapterTitles() {
        return new ArrayList<>(Arrays.asList("Chapter 1", "Chapter 2", "Chapter 3"));
    }

    static ArrayList<String> getAudioUrls() {
        return new ArrayList<>(Arrays.asList(
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        ));
    }
}
