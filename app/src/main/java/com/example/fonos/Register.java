package com.example.fonos;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Register extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private EditText edtFullName;
    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtConfirmPassword;
    private CheckBox cbTerms;
    private TextView btnCreateAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firestore = FirebaseFirestore.getInstance();
        edtFullName = findViewById(R.id.edtFullName);
        edtEmail = findViewById(R.id.edtEmailRegister);
        edtPassword = findViewById(R.id.edtPasswordRegister);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnCreateAccount.setOnClickListener(v -> createAccount());
        findViewById(R.id.txtLoginNow).setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });
    }

    private void createAccount() {
        String fullName = edtFullName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim().toLowerCase(Locale.US);
        String password = edtPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            edtFullName.setError("Vui lòng nhập họ tên");
            edtFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email");
            edtEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            edtPassword.setError("Mật khẩu cần tối thiểu 6 ký tự");
            edtPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            edtConfirmPassword.setError("Mật khẩu nhập lại không khớp");
            edtConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Vui lòng đồng ý điều khoản", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(Register.this, getFirebaseErrorMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        setLoading(false);
                        edtEmail.setError("Email đã được sử dụng");
                        edtEmail.requestFocus();
                        return;
                    }

                    saveUserProfile(fullName, email, password);
                });
    }

    private void saveUserProfile(String fullName, String email, String password) {
        DocumentReference userRef = firestore.collection("users").document();
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", userRef.getId());
        profile.put("fullName", fullName);
        profile.put("email", email);
        profile.put("password", password);
        profile.put("role", "user");
        profile.put("isActive", true);
        profile.put("subscriptionStatus", "inactive");
        profile.put("subscriptionType", "free");
        profile.put("createdAt", FieldValue.serverTimestamp());
        profile.put("updatedAt", FieldValue.serverTimestamp());
        profile.put("lastLoginAt", FieldValue.serverTimestamp());

        userRef
                .set(profile)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(
                                Register.this,
                                getFirebaseErrorMessage(task.getException()),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    userRef.get()
                            .addOnSuccessListener(userDocument -> {
                                setLoading(false);
                                SessionManager.saveUser(Register.this, userDocument);
                                openHomePage();
                            })
                            .addOnFailureListener(error -> {
                                setLoading(false);
                                Toast.makeText(Register.this, getFirebaseErrorMessage(error), Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void setLoading(boolean loading) {
        btnCreateAccount.setEnabled(!loading);
        btnCreateAccount.setText(loading ? "Đang tạo tài khoản..." : "Tạo tài khoản");
    }

    private void openHomePage() {
        Intent intent = new Intent(Register.this, HomePage.class);
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
