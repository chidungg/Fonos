package com.example.fonos;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fonos.database.LocalDatabaseHelper;

import java.util.Locale;

public class Login extends AppCompatActivity {
    private static final String TAG = "SQLiteTest";
    private FirebaseFirestore firestore;
    private EditText edtEmail;
    private EditText edtPassword;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firestore = FirebaseFirestore.getInstance();
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        testLocalDatabase();

        btnLogin.setOnClickListener(v -> loginWithEmail());
        findViewById(R.id.txtRegister).setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, Register.class);
            startActivity(intent);
        });
        findViewById(R.id.txtForgotPassword).setOnClickListener(v -> sendPasswordResetEmail());
    }
    private void testLocalDatabase() {
        new Thread(() -> {
            LocalDatabaseHelper dbHelper = null;
            SQLiteDatabase db = null;
            Cursor cursor = null;

            try {
                dbHelper = new LocalDatabaseHelper(getApplicationContext());
                db = dbHelper.getReadableDatabase();

                cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
                        null
                );

                Log.d(TAG, "SQLite opened successfully");

                while (cursor.moveToNext()) {
                    String tableName = cursor.getString(0);
                    Log.d(TAG, "Table: " + tableName);
                }

            } catch (Exception e) {
                Log.e(TAG, "SQLite test failed: " + e.getMessage(), e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }

                if (db != null && db.isOpen()) {
                    db.close();
                }
            }
        }).start();
    }
    @Override
    protected void onStart() {
        super.onStart();
        if (SessionManager.isLoggedIn(this)) {
            openHomePage();
        }
    }

    private void loginWithEmail() {
        String account = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(account)) {
            edtEmail.setError("Vui lòng nhập email, UID hoặc số điện thoại");
            edtEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu");
            edtPassword.requestFocus();
            return;
        }

        setLoading(true);
        findUserByAccount(account);
    }

    private void findUserByAccount(String account) {
        String normalizedAccount = account.toLowerCase(Locale.US);
        firestore.collection("users")
                .whereEqualTo("email", normalizedAccount)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(Login.this, getFirebaseErrorMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot userDocument = firstDocument(task.getResult());
                    if (userDocument != null) {
                        validatePasswordAndOpen(userDocument);
                    } else {
                        findUserByUid(account);
                    }
                });
    }

    private void findUserByUid(String account) {
        firestore.collection("users")
                .whereEqualTo("uid", account)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(Login.this, getFirebaseErrorMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot userDocument = firstDocument(task.getResult());
                    if (userDocument != null) {
                        validatePasswordAndOpen(userDocument);
                    } else {
                        findUserByPhone(account);
                    }
                });
    }

    private void findUserByPhone(String account) {
        firestore.collection("users")
                .whereEqualTo("phone", account)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (!task.isSuccessful()) {
                        Toast.makeText(Login.this, getFirebaseErrorMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot userDocument = firstDocument(task.getResult());
                    if (userDocument == null) {
                        Toast.makeText(Login.this, "Tài khoản hoặc mật khẩu không đúng", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    validatePasswordAndOpen(userDocument);
                });
    }

    private void validatePasswordAndOpen(DocumentSnapshot userDocument) {
        String pwd = edtPassword.getText().toString().trim();
        String savedPassword = userDocument.getString("password");
        if (!pwd.equals(savedPassword)) {
            setLoading(false);
            Toast.makeText(Login.this, "Tài khoản hoặc mật khẩu không đúng", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(false);
        SessionManager.saveUser(Login.this, userDocument);
        userDocument.getReference().update("lastLoginAt", FieldValue.serverTimestamp());
        openHomePage();
    }

    private DocumentSnapshot firstDocument(QuerySnapshot querySnapshot) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return querySnapshot.getDocuments().get(0);
    }

    private void sendPasswordResetEmail() {
        Toast.makeText(
                this,
                "Chức năng khôi phục mật khẩu cần bật Firebase Authentication",
                Toast.LENGTH_LONG
        ).show();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Đang đăng nhập..." : "Đăng nhập");
    }

    private void openHomePage() {
        Intent intent = new Intent(Login.this, HomePage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String getFirebaseErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null) {
            return "Không kết nối được Firestore";
        }
        return "Không kết nối được Firestore: " + exception.getMessage();
    }
}
