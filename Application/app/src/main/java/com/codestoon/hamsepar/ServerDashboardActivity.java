package com.codestoon.hamsepar;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerDashboardActivity extends AppCompatActivity {

    // UI Components
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtServerStatus, txtServerUrl, txtServerUrlShort, txtLocalIp;
    private TextView txtUserInfo, txtStats, txtClientsCount, txtUploadProgress, txtLimitWarning;
    private TextView txtPremiumBadge;
    private LinearLayout uploadArea;
    private ProgressBar progressBar;
    private ImageView imgQrCode;
    private Button btnCopyUrl, btnDeleteAll;
    private RecyclerView  recyclerClients;
    private LinearLayout layoutFilesContainer;

    // Adapters
    private FileListAdapter fileAdapter;
    private ClientListAdapter clientAdapter;

    // Data
    private List<FileItem> fileList = new ArrayList<>();
    private List<ClientItem> clientList = new ArrayList<>();
    private String currentIp;
    private boolean isPremium = false;
    private boolean deleteProtection = false;
    private String deletePassword = "";
    private String userName;

    // HTTP Client
    private OkHttpClient okHttpClient;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    // File picker
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_dashboard);

        initViews();
        loadUserInfo();
        loadSettings();

        // دریافت IP از Static متغیر MainActivity
        currentIp = MainActivity.CURRENT_SERVER_IP;
        if (currentIp == null || currentIp.isEmpty()) {
            currentIp = "127.0.0.1";
        }

        updateServerInfo();
        setupAdapters();
        setupClickListeners();
        setupFilePicker();
        setupHttpClient();
        startRefreshing();
        generateQrAndDisplay();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        txtServerStatus = findViewById(R.id.txtServerStatus);
        txtServerUrl = findViewById(R.id.txtServerUrl);
        txtServerUrlShort = findViewById(R.id.txtServerUrlShort);
        txtLocalIp = findViewById(R.id.txtLocalIp);
        txtUserInfo = findViewById(R.id.txtUserInfo);
        txtStats = findViewById(R.id.txtStats);
        txtClientsCount = findViewById(R.id.txtClientsCount);
        txtUploadProgress = findViewById(R.id.txtUploadProgress);
        txtLimitWarning = findViewById(R.id.txtLimitWarning);
        txtPremiumBadge = findViewById(R.id.txtPremiumBadge);
        uploadArea = findViewById(R.id.uploadArea);
        progressBar = findViewById(R.id.progressBar);
        imgQrCode = findViewById(R.id.imgQrCode);
        btnCopyUrl = findViewById(R.id.btnCopyUrl);
        btnDeleteAll = findViewById(R.id.btnDeleteAll);
        layoutFilesContainer = findViewById(R.id.layoutFilesContainer);
        recyclerClients = findViewById(R.id.recyclerClients);

        recyclerClients.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadUserInfo() {
        SharedPreferences prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        userName = prefs.getString("user_name", getUserUniqueName());
        txtUserInfo.setText(userName);
    }

    private String getUserUniqueName() {
        String[] persianNames = {"آذرگون", "بارانک", "پونه‌سا", "تیسفون", "جادوک", "چکاد", "خاوران", "دماوند"};
        String randomName = persianNames[(int)(Math.random() * persianNames.length)];
        SharedPreferences prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        prefs.edit().putString("user_name", randomName).apply();
        return randomName;
    }

    private void loadSettings() {
        SharedPreferences securityPrefs = getSharedPreferences("app_security", MODE_PRIVATE);
        deleteProtection = securityPrefs.getBoolean("protect_delete", false);
        deletePassword = securityPrefs.getString("delete_password", "1234");

        SharedPreferences billingPrefs = getSharedPreferences("PREFS_SILENT_SOUND", MODE_PRIVATE);
        isPremium = billingPrefs.getBoolean("premium_activated", false);

        if (isPremium) {
            txtPremiumBadge.setVisibility(View.VISIBLE);
            txtLimitWarning.setVisibility(View.GONE);
        } else {
            txtPremiumBadge.setVisibility(View.GONE);
            txtLimitWarning.setVisibility(View.VISIBLE);
        }

        // Get server IP from MainActivity or get it
        currentIp = getLocalIpAddress();
        updateServerInfo();
    }

    private String getLocalIpAddress() {
        // این متد باید از MainActivity دریافت شود یا خودش محاسبه کند
        // برای سادگی، یک IP پیش‌فرض برمی‌گردانیم
        return "192.168.43.1"; // در عمل از MainActivity بگیرید
    }

    private void updateServerInfo() {
        String url = "http://" + currentIp + ":8080";
        txtServerUrl.setText(url);
        txtServerUrlShort.setText(currentIp + ":8080");
        txtLocalIp.setText(url);
        txtServerStatus.setText("فعال ✅");
    }

    private void setupAdapters() {
        fileAdapter = new FileListAdapter();
        fileAdapter.setListener(new FileListAdapter.OnFileActionListener() {
            @Override
            public void onDownload(String fileName) {
                downloadFile(fileName);
            }

            @Override
            public void onDelete(String fileName) {
                deletesFile(fileName);
            }
        });

        clientAdapter = new ClientListAdapter();
        recyclerClients.setAdapter(clientAdapter);
    }

    private void setupClickListeners() {
        uploadArea.setOnClickListener(v -> openFilePicker());
        btnCopyUrl.setOnClickListener(v -> copyServerUrl());
        btnDeleteAll.setOnClickListener(v -> deleteAllFiles());

        swipeRefresh.setOnRefreshListener(() -> {
            refreshData();
            swipeRefresh.setRefreshing(false);
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadFile(uri);
                    }
                }
        );
    }

    private void openFilePicker() {
        filePickerLauncher.launch("*/*");
    }

    private void setupHttpClient() {
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private void startRefreshing() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshData();
                handler.postDelayed(this, 2500);
            }
        };
        handler.post(refreshRunnable);
    }

    private void refreshData() {
        fetchFilesAndClients();
    }

    private void fetchFilesAndClients() {
        String url = "http://" + currentIp + ":8080/api/files?_=" + System.currentTimeMillis();
        Request request = new Request.Builder().url(url).get().build();

        new Thread(() -> {
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    parseServerResponse(json);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void parseServerResponse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray filesArray = obj.getJSONArray("files");
            fileList.clear();
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileObj = filesArray.getJSONObject(i);
                fileList.add(new FileItem(
                        fileObj.getString("name"),
                        fileObj.getLong("size"),
                        fileObj.getString("modified")
                ));
            }

            JSONArray clientsArray = obj.getJSONArray("clients");
            clientList.clear();
            for (int i = 0; i < clientsArray.length(); i++) {
                JSONObject clientObj = clientsArray.getJSONObject(i);
                clientList.add(new ClientItem(
                        clientObj.getString("name"),
                        clientObj.getString("ip"),
                        clientObj.optString("os", "ناشناس")
                ));
            }

            int totalFiles = obj.getInt("totalFiles");
            long totalSize = obj.getLong("totalSize");

            runOnUiThread(() -> {
                displayFiles(fileList);  // متد جدید
                clientAdapter.setClients(new ArrayList<>(clientList));
                txtClientsCount.setText(String.valueOf(clientList.size()));
                txtStats.setText(String.format(Locale.getDefault(), "📊 %d فایل | حجم کل: %s",
                        totalFiles, formatSize(totalSize)));
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    String fileName;
    private void uploadFile(Uri uri) {
        if (!isPremium) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                long size = is.available();
                if (size > 500 * 1024 * 1024) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "حجم فایل بیش از 500 مگابایت است. نسخه ویژه را تهیه کنید.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String fileName = getFileNameFromUri(uri);
        if (fileName == null) fileName = "file_" + System.currentTimeMillis();
        final String finalFileName = fileName;

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            txtUploadProgress.setVisibility(View.VISIBLE);
            txtUploadProgress.setText("در حال آپلود: 0%");
        });

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String boundary = "*****" + System.currentTimeMillis() + "*****";
                String lineEnd = "\r\n";
                String twoHyphens = "--";

                URL url = new URL("http://" + currentIp + ":8080/upload");
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(120000);

                // دریافت اندازه فایل برای نمایش پیشرفت
                long fileSize;
                try (InputStream sizeIs = getContentResolver().openInputStream(uri)) {
                    fileSize = sizeIs.available();
                }

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

                // نوشتن هدر
                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + finalFileName + "\"" + lineEnd);
                outputStream.writeBytes("Content-Type: application/octet-stream" + lineEnd);
                outputStream.writeBytes(lineEnd);

                // آپلود با بافر - بدون بارگذاری کل فایل در حافظه
                InputStream fileInputStream = getContentResolver().openInputStream(uri);
                byte[] buffer = new byte[8192];  // بافر 8KB
                int bytesRead;
                long totalRead = 0;
                int lastProgress = -1;

                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // به‌روزرسانی پیشرفت (هر 1% یکبار برای کاهش بار UI)
                    final int progress = (int) ((totalRead * 100) / fileSize);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        runOnUiThread(() -> {
                            progressBar.setProgress(progress);
                            txtUploadProgress.setText("در حال آپلود: " + progress + "%");
                        });
                    }
                }

                fileInputStream.close();
                outputStream.writeBytes(lineEnd);
                outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                outputStream.flush();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtUploadProgress.setVisibility(View.GONE);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this, "فایل با موفقیت آپلود شد", Toast.LENGTH_SHORT).show();
                        refreshData();
                    } else if (responseCode == 403) {
                        Toast.makeText(this, "حجم فایل بیش از حد مجاز است. نسخه ویژه را تهیه کنید.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "خطا در آپلود فایل (کد: " + responseCode + ")", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtUploadProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "خطا در آپلود: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private byte[] readBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            if (fileName != null) {
                int lastSlash = fileName.lastIndexOf('/');
                if (lastSlash != -1) {
                    fileName = fileName.substring(lastSlash + 1);
                }
            }
        }
        return fileName;
    }

    private void downloadFile(String fileName) {
        String url = "http://" + currentIp + ":8080/download?file=" + Uri.encode(fileName);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void deletesFile(String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("حذف فایل");
        builder.setMessage("آیا از حذف فایل \"" + fileName + "\" مطمئن هستید؟");

        if (deleteProtection) {
            builder.setNeutralButton("ورود رمز", (dialog, which) -> {
                showPasswordDialog(fileName);
            });
        }

        builder.setPositiveButton("حذف", (dialog, which) -> {
            performDelete(fileName, null);
        });
        builder.setNegativeButton("انصراف", null);
        builder.show();
    }

    private void displayFiles(List<FileItem> files) {
        runOnUiThread(() -> {
            layoutFilesContainer.removeAllViews();

            if (files == null || files.isEmpty()) {
                TextView emptyView = new TextView(this);
                emptyView.setText("📂 هیچ فایلی وجود ندارد");
                emptyView.setTextColor(ContextCompat.getColor(this, R.color.gray_500));
                emptyView.setGravity(Gravity.CENTER);
                emptyView.setPadding(32, 32, 32, 32);
                emptyView.setTextSize(14);
                layoutFilesContainer.addView(emptyView);
                return;
            }

            for (FileItem file : files) {
                View itemView = LayoutInflater.from(this).inflate(R.layout.item_file, layoutFilesContainer, false);

                TextView txtIcon = itemView.findViewById(R.id.txtFileIcon);
                TextView txtName = itemView.findViewById(R.id.txtFileName);
                TextView txtSize = itemView.findViewById(R.id.txtFileSize);
                Button btnDownload = itemView.findViewById(R.id.btnDownload);
                Button btnDelete = itemView.findViewById(R.id.btnDelete);

                txtIcon.setText(getFileIcon(file.getName()));
                txtName.setText(file.getName());
                txtSize.setText(formatSize(file.getSize()));

                btnDownload.setOnClickListener(v -> downloadFile(file.getName()));
                btnDelete.setOnClickListener(v -> deleteFile(file.getName()));

                layoutFilesContainer.addView(itemView);
            }
        });
    }

    // متد کمکی برای آیکون فایل:
    private String getFileIcon(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": return "🖼️";
            case "mp4": case "mkv": case "avi": return "🎬";
            case "mp3": case "wav": case "flac": return "🎵";
            case "pdf": return "📕";
            case "doc": case "docx": return "📄";
            case "apk": return "📦";
            default: return "📁";
        }
    }



    private void showPasswordDialog(String fileName) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("رمز حذف");

        new AlertDialog.Builder(this)
                .setTitle("تأیید رمز")
                .setView(input)
                .setPositiveButton("تأیید", (dialog, which) -> {
                    String pass = input.getText().toString();
                    performDelete(fileName, pass);
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void performDelete(String fileName, String password) {
        new Thread(() -> {
            try {
                okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder()
                        .add("fileName", fileName);
                if (password != null) {
                    formBuilder.add("password", password);
                }

                Request request = new Request.Builder()
                        .url("http://" + currentIp + ":8080/delete")
                        .post(formBuilder.build())
                        .build();

                Response response = okHttpClient.newCall(request).execute();

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(this, "فایل حذف شد", Toast.LENGTH_SHORT).show();
                        refreshData();
                    } else if (response.code() == 401) {
                        Toast.makeText(this, "رمز اشتباه است", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "خطا در حذف فایل", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "خطا: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void deleteAllFiles() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("حذف همه فایل‌ها");
        builder.setMessage("آیا از حذف همه فایل‌ها مطمئن هستید؟");

        if (deleteProtection) {
            builder.setNeutralButton("ورود رمز", (dialog, which) -> {
                showPasswordForDeleteAll();
            });
        }

        builder.setPositiveButton("حذف همه", (dialog, which) -> {
            performDeleteAll(null);
        });
        builder.setNegativeButton("انصراف", null);
        builder.show();
    }

    private void showPasswordForDeleteAll() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("رمز حذف");

        new AlertDialog.Builder(this)
                .setTitle("تأیید رمز")
                .setView(input)
                .setPositiveButton("تأیید", (dialog, which) -> {
                    performDeleteAll(input.getText().toString());
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void performDeleteAll(String password) {
        new Thread(() -> {
            try {
                okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder();
                if (password != null) {
                    formBuilder.add("password", password);
                }

                Request request = new Request.Builder()
                        .url("http://" + currentIp + ":8080/delete-all")
                        .post(formBuilder.build())
                        .build();

                Response response = okHttpClient.newCall(request).execute();

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(this, "همه فایل‌ها حذف شدند", Toast.LENGTH_SHORT).show();
                        refreshData();
                    } else if (response.code() == 401) {
                        Toast.makeText(this, "رمز اشتباه است", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "خطا در حذف فایل‌ها", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "خطا: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void copyServerUrl() {
        String url = txtServerUrl.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Server URL", url);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "لینک کپی شد", Toast.LENGTH_SHORT).show();
    }

    private void generateQrAndDisplay() {
        String url = txtServerUrl.getText().toString();
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300);
            Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.RGB_565);
            for (int x = 0; x < 300; x++) {
                for (int y = 0; y < 300; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            imgQrCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}