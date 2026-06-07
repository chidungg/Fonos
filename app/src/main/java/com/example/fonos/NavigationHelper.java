package com.example.fonos;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

final class NavigationHelper {

    private NavigationHelper() {
    }

    static void setupBottomNavigation(AppCompatActivity activity, Class<?> currentActivity) {
        bindNavigationItem(activity, R.id.navHome, HomePage.class, currentActivity);
        bindNavigationItem(activity, R.id.navExplore, Search.class, currentActivity);
        bindNavigationItem(activity, R.id.navLibrary, Library.class, currentActivity);
        bindNavigationItem(activity, R.id.navCategory, Category.class, currentActivity);
        bindNavigationItem(activity, R.id.navProfile, Profile.class, currentActivity);
    }

    private static void bindNavigationItem(
            AppCompatActivity activity,
            int viewId,
            Class<?> targetActivity,
            Class<?> currentActivity
    ) {
        View navigationItem = activity.findViewById(viewId);
        if (navigationItem == null || targetActivity == currentActivity) {
            return;
        }

        navigationItem.setOnClickListener(v -> {
            Intent intent = new Intent(activity, targetActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
        });
    }
}
