package com.codestoon.hamsepar;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppExtractorActivity extends AppCompatActivity implements AppListAdapter.OnAppSelectionListener {

    private ImageView ivBack;
    private RecyclerView recyclerApps;
    private Button btnSelectAll, btnDeselectAll, btnExtract;
    private ProgressBar progressBar;

    private AppListAdapter adapter;
    private PackageManager packageManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<AppItem> allApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_extractor);

        initViews();
        setupListeners();
        loadInstalledApps();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        recyclerApps = findViewById(R.id.recyclerApps);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeselectAll = findViewById(R.id.btnDeselectAll);
        btnExtract = findViewById(R.id.btnExtract);
        progressBar = findViewById(R.id.progressBar);

        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter();
        adapter.setListener(this);
        recyclerApps.setAdapter(adapter);

        packageManager = getPackageManager();
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        btnDeselectAll.setOnClickListener(v -> adapter.deselectAll());
        btnExtract.setOnClickListener(v -> extractSelectedApps());
    }

    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);

        new Thread(() -> {
            List<AppItem> appList = new ArrayList<>();

            try {
                // روش جایگزین: استفاده از GET_ACTIVITIES
                List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

                for (PackageInfo packageInfo : packages) {
                    // فیلتر کردن برنامه‌های سیستمی (اختیاری - حذف کنید تا همه برنامه‌ها را ببیند)
                    if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        // اگر می‌خواهید برنامه‌های سیستمی را هم نشان دهد، این خط را کامنت کنید
                        // continue;
                    }

                    String appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                    String packageName = packageInfo.packageName;
                    String sourceDir = packageInfo.applicationInfo.sourceDir;

                    // بررسی دسترسی به فایل APK
                    File apkFile = new File(sourceDir);
                    if (apkFile.exists()) {
                        AppItem app = new AppItem(appName, packageName, sourceDir,
                                packageInfo.applicationInfo.loadIcon(packageManager));
                        appList.add(app);
                    }
                }

                // مرتب‌سازی
                appList.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            } catch (Exception e) {
                e.printStackTrace();
            }

            final List<AppItem> finalList = appList;
            mainHandler.post(() -> {
                adapter.setAppList(finalList);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, finalList.size() + " برنامه یافت شد", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    int i;
    private void extractSelectedApps() {
        List<AppItem> selectedApps = adapter.getSelectedApps();

        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "لطفاً حداقل یک برنامه را انتخاب کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        File destFolder = getSharedFilesFolder();
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }

        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(100);
        progressBar.setIndeterminate(false);
        btnExtract.setEnabled(false);

        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> failedApps = new ArrayList<>();

            for (i = 0; i < selectedApps.size(); i++) {
                AppItem app = selectedApps.get(i);
                final int progress = (int) ((i + 1) * 100.0 / selectedApps.size());

                mainHandler.post(() -> {
                    progressBar.setProgress(progress);
                    btnExtract.setText("در حال استخراج: " + (i + 1) + "/" + selectedApps.size());
                });

                boolean success = copyApkFile(app, destFolder);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                    failedApps.add(app.getAppName());
                }
            }

            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            final List<String> finalFailedApps = failedApps;

            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnExtract.setEnabled(true);
                btnExtract.setText("📦 استخراج APK (" + selectedApps.size() + ")");

                String message = "✅ " + finalSuccessCount + " برنامه با موفقیت استخراج شد.\n📁 مسیر: Documents/Hamsepar";
                if (finalFailCount > 0) {
                    message += "\n\n❌ خطا در استخراج " + finalFailCount + " برنامه:\n" + String.join("\n", finalFailedApps);
                    message += "\n\n⚠️ نکته: برخی برنامه‌های سیستمی یا محافظت شده قابل استخراج نیستند.";
                }

                new AlertDialog.Builder(AppExtractorActivity.this)
                        .setTitle("نتیجه استخراج")
                        .setMessage(message)
                        .setPositiveButton("باشه", null)
                        .show();
            });
        }).start();
    }

    private boolean copyApkFile(AppItem app, File destFolder) {
        File sourceFile = new File(app.getSourceDir());

        if (!sourceFile.exists()) {
            return false;
        }

        // پاکسازی نام فایل برای استفاده در مسیر
        String safeFileName = app.getAppName()
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim();

        // اگر نام فایل خالی شد، از package name استفاده کن
        if (safeFileName.isEmpty()) {
            safeFileName = app.getPackageName();
        }

        String destFileName = safeFileName + "_" + app.getPackageName() + ".apk";
        File destFile = new File(destFolder, destFileName);

        // اگر فایل قبلاً وجود دارد، نام جدید بساز
        int counter = 1;
        while (destFile.exists()) {
            destFileName = safeFileName + "_" + app.getPackageName() + "_" + counter + ".apk";
            destFile = new File(destFolder, destFileName);
            counter++;
        }

        try {
            copyFile(sourceFile, destFile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    private File getSharedFilesFolder() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hamsepar");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        btnExtract.setText("📦 استخراج APK (" + selectedCount + ")");
        btnExtract.setEnabled(selectedCount > 0);
    }
}