package com.codestoon.hamsepar;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CommentNegativeActivity extends AppCompatActivity {

    private ImageView ivBack;
    private EditText etName;
    private EditText etComment;
    private LinearLayout llSendComment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment_negative);

        initViews();
        setupListeners();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        etName = findViewById(R.id.etName);
        etComment = findViewById(R.id.etComment);
        llSendComment = findViewById(R.id.llSendComment);
    }

    private void setupListeners() {
        // دکمه بازگشت
        ivBack.setOnClickListener(v -> finish());

        // ارسال دیدگاه منفی
        llSendComment.setOnClickListener(v -> sendNegativeComment());
    }

    private void sendNegativeComment() {
        String name = etName.getText().toString().trim();
        String comment = etComment.getText().toString().trim();

        // اعتبارسنجی فیلدها
        if (name.isEmpty()) {
            etName.setError("لطفاً نام خود را وارد کنید");
            etName.requestFocus();
            return;
        }

        if (comment.isEmpty()) {
            etComment.setError("لطفاً متن پیام را وارد کنید");
            etComment.requestFocus();
            return;
        }

        if (comment.length() < 5) {
            etComment.setError("لطفاً نظر خود را با جزئیات بیشتری بنویسید");
            etComment.requestFocus();
            return;
        }

        // ذخیره نظر منفی
        saveNegativeCommentToLocal(name, comment);
        sendFeedbackToSupport(name, comment);
    }

    private void saveNegativeCommentToLocal(String name, String comment) {
        // ذخیره در SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("user_feedback", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("last_negative_name", name);
        editor.putString("last_negative_comment", comment);
        editor.putLong("last_negative_time", System.currentTimeMillis());
        editor.apply();
    }

    private void sendFeedbackToSupport(String name, String comment) {
        // ارسال نظر از طریق ایمیل
        try {
            Toast.makeText(CommentNegativeActivity.this,
                    "از بازخورد شما متشکریم.",
                    Toast.LENGTH_LONG).show();
            finish();

        } catch (Exception e) {
            Toast.makeText(CommentNegativeActivity.this,
                    "از بازخورد شما متشکریم.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }


}