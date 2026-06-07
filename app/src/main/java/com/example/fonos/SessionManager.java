package com.example.fonos;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

final class SessionManager {
    private static final String PREFS_NAME = "fonos_session";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_ROLE = "role";

    private SessionManager() {
    }

    static void saveUser(Context context, DocumentSnapshot userDocument) {
        preferences(context).edit()
                .putString(KEY_UID, userDocument.getId())
                .putString(KEY_EMAIL, userDocument.getString("email"))
                .putString(KEY_FULL_NAME, userDocument.getString("fullName"))
                .putString(KEY_ROLE, userDocument.getString("role"))
                .apply();
    }

    static boolean isLoggedIn(Context context) {
        return getUid(context) != null;
    }

    @Nullable
    static String getUid(Context context) {
        return preferences(context).getString(KEY_UID, null);
    }

    static void clear(Context context) {
        preferences(context).edit().clear().apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
