package com.example.fonos;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomePage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        NavigationHelper.setupBottomNavigation(this, HomePage.class);
        bindBookDetails(R.id.bookFeatured);
        bindBookDetails(R.id.btnListenTrial);
        bindBookDetails(R.id.bookContinue);
        bindBookDetails(R.id.bookSuggestion1);
        bindBookDetails(R.id.bookSuggestion2);
        bindBookDetails(R.id.bookSuggestion3);
    }

    private void bindBookDetails(int viewId) {
        findViewById(viewId).setOnClickListener(v -> openBookDetails());
    }

    private void openBookDetails() {
        startActivity(new Intent(this, BookDetails.class));
    }
}
