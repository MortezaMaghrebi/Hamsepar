package com.codestoon.hamsepar;


import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class CommentActivity extends AppCompatActivity {

    private ImageView ivBack;
    private LinearLayout yesBtn;
    private LinearLayout noBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        initViews();
        setupListeners();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        yesBtn = findViewById(R.id.yesBtn);
        noBtn = findViewById(R.id.noBtn);
    }

    private void setupListeners() {
        // دکمه بازگشت
        ivBack.setOnClickListener(v -> finish());

        // دکمه بله (ارسال نظر مثبت به مایکت)
        yesBtn.setOnClickListener(v -> sendPositiveCommentToStore());

        // دکمه خیر (باز کردن اکتیویتی نظر منفی)
        noBtn.setOnClickListener(v -> {
            Intent intent = new Intent(CommentActivity.this, CommentNegativeActivity.class);
            startActivity(intent);
        });
    }

    private void sendPositiveCommentToStore() {
        // ارسال نظر رضایت به مایکت
        try {
            StoreIntents.openStoreForComment(CommentActivity.this);
            finish();
        } catch (Exception e) {
            Toast.makeText(CommentActivity.this,
                    "خطا در ارسال نظر به مایکت",
                    Toast.LENGTH_SHORT).show();
        }
    }
}