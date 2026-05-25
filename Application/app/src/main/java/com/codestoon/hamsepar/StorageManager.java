package com.codestoon.hamsepar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import java.io.File;

public class StorageManager {

    private static final String PREFS_NAME = "storage_prefs";
    private static final String KEY_STORAGE_MODE = "storage_mode";
    private static final String KEY_FULL_ACCESS_GRANTED = "full_access_granted";

    // حالت‌های ذخیره‌سازی
    public static final int MODE_FULL_ACCESS = 1;      // دسترسی کامل - پوشه Documents/Hamsepar
    public static final int MODE_PRIVATE_ACCESS = 2;   // دسترسی محدود - پوشه خصوصی اپ

    private Context context;
    private SharedPreferences prefs;

    public StorageManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * بررسی آیا کاربر دسترسی کامل به حافظه دارد
     */
    public boolean hasFullStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            // در اندروید 10 و پایین‌تر، دسترسی معمولی کافی است
            return true;
        }
    }

    /**
     * ذخیره می‌کند که کاربر دسترسی کامل را داده است
     */
    public void setFullAccessGranted(boolean granted) {
        prefs.edit().putBoolean(KEY_FULL_ACCESS_GRANTED, granted).apply();
        if (granted) {
            prefs.edit().putInt(KEY_STORAGE_MODE, MODE_FULL_ACCESS).apply();
        }
    }

    /**
     * آیا کاربر قبلاً دسترسی کامل را داده است؟
     */
    public boolean isFullAccessGranted() {
        return prefs.getBoolean(KEY_FULL_ACCESS_GRANTED, false);
    }

    /**
     * تنظیم حالت ذخیره‌سازی (با توجه به انتخاب کاربر)
     */
    public void setStorageMode(int mode) {
        prefs.edit().putInt(KEY_STORAGE_MODE, mode).apply();
    }

    /**
     * دریافت حالت فعلی ذخیره‌سازی
     */
    public int getStorageMode() {
        // اگر حالت ذخیره نشده، بررسی کن آیا دسترسی کامل داریم یا نه
        if (!prefs.contains(KEY_STORAGE_MODE)) {
            if (hasFullStorageAccess() && isFullAccessGranted()) {
                return MODE_FULL_ACCESS;
            } else {
                return MODE_PRIVATE_ACCESS;
            }
        }
        return prefs.getInt(KEY_STORAGE_MODE, MODE_PRIVATE_ACCESS);
    }

    /**
     * دریافت مسیر اصلی ذخیره‌سازی فایل‌ها
     */
    public File getStorageDirectory() {
        int mode = getStorageMode();

        if (mode == MODE_FULL_ACCESS) {
            // مسیر عمومی: Documents/Hamsepar
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hamsepar");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            return folder;
        } else {
            // مسیر خصوصی: Android/data/package_name/files/SharedFiles
            File folder = new File(context.getExternalFilesDir(null), "SharedFiles");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            return folder;
        }
    }



    /**
     * آیا فایل در مسیر عمومی ذخیره می‌شود؟ (برای نمایش به کاربر)
     */
    public boolean isUsingPublicDirectory() {
        return getStorageMode() == MODE_FULL_ACCESS;
    }

    /**
     * دریافت مسیر به صورت رشته (برای نمایش)
     */
    public String getStoragePath() {
        return getStorageDirectory().getAbsolutePath();
    }

    /**
     * ریست کردن تنظیمات (برای دیباگ)
     */
    public void resetSettings() {
        prefs.edit().clear().apply();
    }
}