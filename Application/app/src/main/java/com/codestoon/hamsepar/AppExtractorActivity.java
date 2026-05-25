package com.codestoon.hamsepar;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class AppExtractorActivity extends AppCompatActivity {

    private RecyclerView recyclerApps;
    private MaterialButton btnExtract;
    private EditText edtSearch;
    private TextView txtSelectedCount, btnSelectAll, btnDeselectAll;
    private FrameLayout loadingLayout;
    private LinearLayout progressLayout;
    private ProgressBar progressBar;
    private TextView txtProgress;
    private TabLayout tabLayout;

    private ImageView btnClearSearch;
    private AppListAdapter adapter;
    private PackageManager packageManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<AppItem> allApps = new ArrayList<>();
    private List<AppItem> userApps = new ArrayList<>();
    private List<AppItem> systemApps = new ArrayList<>();
    private int currentTab = 0;

    private StorageManager storageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_extractor);

        storageManager = new StorageManager(this);
        initViews();
        setupListeners();
        loadInstalledApps();
    }

    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back);

        recyclerApps = findViewById(R.id.recyclerApps);
        btnExtract = findViewById(R.id.btnExtract);
        edtSearch = findViewById(R.id.edtSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        txtSelectedCount = findViewById(R.id.txtSelectedCount);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeselectAll = findViewById(R.id.btnDeselectAll);
        loadingLayout = findViewById(R.id.loadingLayout);
        progressLayout = findViewById(R.id.progressLayout);
        progressBar = findViewById(R.id.progressBar);
        txtProgress = findViewById(R.id.txtProgress);
        tabLayout = findViewById(R.id.tabLayout);

        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter();
        adapter.setOnSelectionChangedListener(selectedCount -> updateSelectionUI());
        recyclerApps.setAdapter(adapter);

        packageManager = getPackageManager();

        updateSelectionUI();
    }

    private void setupListeners() {
        btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        btnDeselectAll.setOnClickListener(v -> adapter.deselectAll());
        btnExtract.setOnClickListener(v -> extractSelectedApps());

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                adapter.setSearchQuery(query);
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            edtSearch.setText("");
            btnClearSearch.setVisibility(View.GONE);
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateDisplayedApps();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateSelectionUI() {
        int count = adapter.getSelectedCount();
        txtSelectedCount.setText(count + " انتخاب شده");
        btnExtract.setEnabled(count > 0);
        if (count > 0) {
            btnExtract.setText("📦 استخراج " + count + " فایل APK");
        } else {
            btnExtract.setText("📦 استخراج فایل APK");
        }
    }

    private void updateDisplayedApps() {
        String query = edtSearch.getText().toString();
        if (currentTab == 0) {
            adapter.setAppList(userApps, query);
        } else {
            adapter.setAppList(systemApps, query);
        }
        updateSelectionUI();
    }

    private void loadInstalledApps() {
        loadingLayout.setVisibility(View.VISIBLE);
        recyclerApps.setVisibility(View.GONE);

        new Thread(() -> {
            userApps.clear();
            systemApps.clear();

            try {
                List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

                for (PackageInfo packageInfo : packages) {
                    ApplicationInfo appInfo = packageInfo.applicationInfo;

                    String appName = packageManager.getApplicationLabel(appInfo).toString();
                    String packageName = appInfo.packageName;
                    String sourceDir = appInfo.sourceDir;
                    boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    File apkFile = new File(sourceDir);
                    if (apkFile.exists()) {
                        AppItem app = new AppItem(appName, packageName, sourceDir,
                                appInfo.loadIcon(packageManager), isSystemApp);

                        if (isSystemApp) {
                            systemApps.add(app);
                        } else {
                            userApps.add(app);
                        }
                    }
                }

                userApps.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
                systemApps.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            } catch (Exception e) {
                e.printStackTrace();
            }

            mainHandler.post(() -> {
                loadingLayout.setVisibility(View.GONE);
                recyclerApps.setVisibility(View.VISIBLE);
                updateDisplayedApps();

                TabLayout.Tab userTab = tabLayout.getTabAt(0);
                TabLayout.Tab systemTab = tabLayout.getTabAt(1);
                if (userTab != null) userTab.setText("📱 کاربر (" + userApps.size() + ")");
                if (systemTab != null) systemTab.setText("⚙️ سیستمی (" + systemApps.size() + ")");

                Toast.makeText(this, userApps.size() + " برنامه کاربر، " + systemApps.size() + " برنامه سیستمی", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void extractSelectedApps() {
        List<AppItem> selectedApps = adapter.getSelectedApps();

        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "لطفاً حداقل یک برنامه را انتخاب کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        File destFolder = storageManager.getStorageDirectory();
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }

        progressLayout.setVisibility(View.VISIBLE);
        progressBar.setMax(selectedApps.size());
        progressBar.setProgress(0);
        btnExtract.setEnabled(false);
        btnSelectAll.setEnabled(false);
        btnDeselectAll.setEnabled(false);
        edtSearch.setEnabled(false);

        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> failedApps = new ArrayList<>();

            for (int i = 0; i < selectedApps.size(); i++) {
                AppItem app = selectedApps.get(i);
                final int current = i + 1;

                mainHandler.post(() -> {
                    progressBar.setProgress(current);
                    txtProgress.setText("در حال استخراج: " + current + " از " + selectedApps.size());
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
                progressLayout.setVisibility(View.GONE);
                btnExtract.setEnabled(true);
                btnSelectAll.setEnabled(true);
                btnDeselectAll.setEnabled(true);
                edtSearch.setEnabled(true);
                btnExtract.setText("📦 استخراج APK");

                String message = "✅ " + finalSuccessCount + " برنامه با موفقیت استخراج شد.\n📁 مسیر: " + destFolder.getAbsolutePath();
                if (finalFailCount > 0) {
                    message += "\n\n❌ خطا در " + finalFailCount + " برنامه:\n" + String.join("\n", finalFailedApps);
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
        if (!sourceFile.exists()) return false;

        String packageName = app.getPackageName();
        String shortName = getShortAppName(packageName);

        String destFileName = shortName + ".apk";
        File destFile = new File(destFolder, destFileName);

        int counter = 1;
        while (destFile.exists()) {
            destFileName = shortName + "_" + counter + ".apk";
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

    private String getShortAppName(String packageName) {
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.equalsIgnoreCase("android") || lastPart.equalsIgnoreCase("app")) {
                if (parts.length >= 3) {
                    return parts[parts.length - 2];
                }
            }
            return lastPart;
        }
        return packageName;
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}