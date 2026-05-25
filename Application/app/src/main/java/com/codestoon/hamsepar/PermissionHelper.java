package com.codestoon.hamsepar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;

public class PermissionHelper {

    private static final int REQUEST_CODE_FULL_ACCESS = 6100;
    private static final int REQUEST_CODE_NORMAL_STORAGE = 6101;

    private Activity activity;
    private StorageManager storageManager;
    private PermissionCallback callback;

    public interface PermissionCallback {
        void onPermissionGranted(boolean isFullAccess);
        void onPermissionDenied();
    }

    public PermissionHelper(Activity activity) {
        this.activity = activity;
        this.storageManager = new StorageManager(activity);
    }

    /**
     * نمایش دیالوگ توضیحی به کاربر قبل از درخواست دسترسی
     */
    public void showExplanationDialog(PermissionCallback callback) {
        this.callback = callback;

        // لینک گیت‌هاب
        String githubLink = "https://github.com/mortezamaghrebi/Hamsepar";

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("🔐 دسترسی به حافظه");
        builder.setMessage(
                "📁 برنامه همسپار برای ذخیره فایل‌ها در پوشه‌ای که با فایل منیجر قابل مشاهده باشد، نیاز به دسترسی به حافظه دارد.\n\n" +
                        "✅ این برنامه **متن‌باز (Open Source)** است و کد آن در گیت‌هاب منتشر شده:\n" +
                        githubLink + "\n\n" +
                        "🔒 امنیت برنامه تضمین شده و هیچ دسترسی غیرضروری ندارد.\n\n" +
                        "🔓 اگر به برنامه **اطمینان دارید**، گزینه «دسترسی کامل» را انتخاب کنید تا فایل‌ها در پوشه Documents/Hamsepar ذخیره شوند.\n\n" +
                        "🛡️ اگر **اطمینان ندارید**، گزینه «دسترسی محدود» را انتخاب کنید. برنامه همچنان کار می‌کند ولی فایل‌ها در پوشه خصوصی برنامه ذخیره می‌شوند (با فایل منیجر معمولی قابل مشاهده نیستند)."
        );
        builder.setPositiveButton("✅ دسترسی کامل (توصیه شده)", (dialog, which) -> {
            requestFullStorageAccess();
        });
        builder.setNeutralButton("🛡️ دسترسی محدود", (dialog, which) -> {
            // کاربر دسترسی محدود را انتخاب کرد
            storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
            storageManager.setFullAccessGranted(false);
            if (callback != null) {
                callback.onPermissionGranted(false);
            }
        });
        builder.setNegativeButton("❌ بعداً", (dialog, which) -> {
            if (callback != null) {
                callback.onPermissionDenied();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * درخواست دسترسی کامل به حافظه (MANAGE_EXTERNAL_STORAGE)
     */
    private void requestFullStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", activity.getPackageName())));
                activity.startActivityForResult(intent, REQUEST_CODE_FULL_ACCESS);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, REQUEST_CODE_FULL_ACCESS);
            }
        } else {
            // برای اندروید 10 و پایین‌تر، دسترسی معمولی کافی است
            requestNormalStoragePermission();
        }
    }

    /**
     * درخواست دسترسی معمولی (READ/WRITE_EXTERNAL_STORAGE)
     */
    private void requestNormalStoragePermission() {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_CODE_NORMAL_STORAGE);
    }

    /**
     * بررسی نتیجه مجوزها (در onRequestPermissionsResult صدا زده شود)
     */
    public void handleRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_NORMAL_STORAGE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                storageManager.setStorageMode(StorageManager.MODE_FULL_ACCESS);
                storageManager.setFullAccessGranted(true);
                Toast.makeText(activity, "✅ دسترسی به حافظه داده شد", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onPermissionGranted(true);
                }
            } else {
                // اگر دسترسی کامل داده نشد، به حالت محدود برو
                storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
                storageManager.setFullAccessGranted(false);
                Toast.makeText(activity, "⚠️ برنامه با دسترسی محدود ادامه می‌دهد", Toast.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onPermissionGranted(false);
                }
            }
        }
    }

    /**
     * بررسی نتیجه onActivityResult برای دسترسی کامل (اندروید 11+)
     */
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FULL_ACCESS) {
            if (storageManager.hasFullStorageAccess()) {
                // کاربر دسترسی کامل را داد
                storageManager.setStorageMode(StorageManager.MODE_FULL_ACCESS);
                storageManager.setFullAccessGranted(true);
                Toast.makeText(activity, "✅ دسترسی کامل به حافظه داده شد", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onPermissionGranted(true);
                }
            } else {
                // کاربر دسترسی کامل را نداد، به حالت محدود برو
                storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
                storageManager.setFullAccessGranted(false);
                Toast.makeText(activity, "⚠️ دسترسی کامل داده نشد. برنامه با دسترسی محدود ادامه می‌دهد.", Toast.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onPermissionGranted(false);
                }
            }
        }
    }

    /**
     * بررسی وضعیت فعلی و در صورت نیاز نمایش دیالوگ
     * اگر قبلاً تصمیم گرفته شده، دیالوگ نمایش داده نمی‌شود
     */
    public void checkAndRequestIfNeeded(PermissionCallback callback) {
        this.callback = callback;

        // اگر کاربر قبلاً دسترسی کامل را داده
        if (storageManager.isFullAccessGranted() && storageManager.hasFullStorageAccess()) {
            storageManager.setStorageMode(StorageManager.MODE_FULL_ACCESS);
            if (callback != null) {
                callback.onPermissionGranted(true);
            }
            return;
        }

        // اگر کاربر قبلاً حالت محدود را انتخاب کرده
        if (storageManager.getStorageMode() == StorageManager.MODE_PRIVATE_ACCESS) {
            if (callback != null) {
                callback.onPermissionGranted(false);
            }
            return;
        }

        // اولین بار است - دیالوگ توضیحی را نشان بده
        showExplanationDialog(callback);
    }
}