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

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class Login extends AppCompatActivity {

    private FirebaseAuth auth;
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

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> loginWithEmail());
        findViewById(R.id.txtRegister).setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, Register.class);
            startActivity(intent);
        });
        findViewById(R.id.txtForgotPassword).setOnClickListener(v -> sendPasswordResetEmail());
        findViewById(R.id.btnGoogle).setOnClickListener(v -> showProviderNotConfigured());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (SessionManager.isLoggedIn(this)) {
            setLoading(true);
            loadProfileAndOpen(auth.getCurrentUser());
        }
    }

    private void loginWithEmail() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email");
            edtEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu");
            edtPassword.requestFocus();
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(Login.this, getAuthErrorMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }

                    loadProfileAndOpen(auth.getCurrentUser());
                });
    }

    private void loadProfileAndOpen(FirebaseUser user) {
        if (user == null) {
            setLoading(false);
            Toast.makeText(this, "Phiên đăng nhập không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(userDocument -> {
                    if (!userDocument.exists()) {
                        auth.signOut();
                        SessionManager.clear(Login.this);
                        setLoading(false);
                        Toast.makeText(
                                Login.this,
                                "Không tìm thấy hồ sơ người dùng trong Firestore",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    saveSessionAndOpen(userDocument);
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    Toast.makeText(Login.this, getFirestoreErrorMessage(error), Toast.LENGTH_LONG).show();
                });
    }

    private void saveSessionAndOpen(DocumentSnapshot userDocument) {
        setLoading(false);
        SessionManager.saveUser(this, userDocument);
        userDocument.getReference().update("lastLoginAt", FieldValue.serverTimestamp());
        openHomePage();
    }

    private void sendPasswordResetEmail() {
        String email = edtEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email để khôi phục mật khẩu");
            edtEmail.requestFocus();
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> Toast.makeText(
                        this,
                        "Đã gửi email khôi phục mật khẩu",
                        Toast.LENGTH_LONG
                ).show())
                .addOnFailureListener(error -> Toast.makeText(
                        this,
                        getAuthErrorMessage(error),
                        Toast.LENGTH_LONG
                ).show());
    }

    private void showProviderNotConfigured() {
        Toast.makeText(this, "Đăng nhập bằng nhà cung cấp này chưa được cấu hình", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Đang đăng nhập..." : "Đăng nhập");
    }

    private void openHomePage() {
        Intent intent = new Intent(this, HomePage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String getAuthErrorMessage(Exception exception) {
        if (exception instanceof FirebaseNetworkException) {
            return "Không kết nối được Firebase. Vui lòng kiểm tra Internet.";
        }

        if (exception instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) exception).getErrorCode();
            switch (errorCode) {
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "Firebase Authentication chưa bật Email/Password. Vào Firebase Console > Authentication > Sign-in method để bật.";
                case "ERROR_USER_NOT_FOUND":
                    return "Tài khoản này chưa có trong Firebase Authentication. Nếu đây là tài khoản cũ trong Firestore, hãy đăng ký lại hoặc migrate tài khoản.";
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                    return "Email hoặc mật khẩu không đúng.";
                case "ERROR_INVALID_EMAIL":
                    return "Email không hợp lệ.";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Tài khoản đang bị giới hạn tạm thời do thử đăng nhập quá nhiều lần.";
                case "ERROR_USER_DISABLED":
                    return "Tài khoản này đã bị vô hiệu hóa.";
                default:
                    return "Không xác thực được tài khoản (" + errorCode + "): " + exception.getMessage();
            }
        }

        if (exception instanceof FirebaseAuthInvalidUserException
                || exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "Email hoặc mật khẩu không đúng.";
        }
        if (exception == null || exception.getMessage() == null) {
            return "Không xác thực được tài khoản.";
        }
        return "Không xác thực được tài khoản: " + exception.getMessage();
    }

    private String getFirestoreErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null) {
            return "Không kết nối được Firestore";
        }
        return "Không kết nối được Firestore: " + exception.getMessage();
    }
}
