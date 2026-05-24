package com.codestoon.hamsepar;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import java.util.List;

public class AppExtractorActivity extends AppCompatActivity implements AppListAdapter.OnAppSelectionListener {

    private ImageView ivBack;
    private RecyclerView recyclerApps;
    private Button btnSelectAll, btnDeselectAll, btnExtract;
    private ProgressBar progressBar;

    private AppListAdapter adapter;
    private PackageManager packageManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_extractor);  // نام فایل اصلاح شد

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
        List<AppItem> appList = new ArrayList<>();

        List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : packages) {
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            String packageName = appInfo.packageName;
            String sourceDir = appInfo.sourceDir;

            if (sourceDir != null && new File(sourceDir).exists()) {
                AppItem app = new AppItem(appName, packageName, sourceDir, appInfo.loadIcon(packageManager));
                appList.add(app);
            }
        }

        // مرتب‌سازی بر اساس نام
        appList.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

        adapter.setAppList(appList);
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
        btnExtract.setEnabled(false);

        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> failedApps = new ArrayList<>();

            for ( i = 0; i < selectedApps.size(); i++) {
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
                }

                new AlertDialog.Builder(this)
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

        String destFileName = safeFileName + "_" + app.getPackageName() + ".apk";
        File destFile = new File(destFolder, destFileName);

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