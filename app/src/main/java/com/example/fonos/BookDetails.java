package com.example.fonos;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BookDetails extends AppCompatActivity {

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

        findViewById(R.id.btnPlaySample).setOnClickListener(v -> openPlayer(0));
        findViewById(R.id.chapter1).setOnClickListener(v -> openPlayer(0));
        findViewById(R.id.chapter2).setOnClickListener(v -> openPlayer(1));
        findViewById(R.id.chapter3).setOnClickListener(v -> openPlayer(2));
    }

    private void openPlayer(int startChapterIndex) {
        startActivity(DemoAudioBook.createPlayerIntent(this, startChapterIndex));
    }
}
