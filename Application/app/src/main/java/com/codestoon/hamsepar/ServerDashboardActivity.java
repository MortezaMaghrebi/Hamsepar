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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
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
import androidx.core.content.FileProvider;
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
    private TextView txtUserInfo, txtStats, txtClientsCount, txtUploadProgress, txtLimitWarning, txtStorageMode;
    private TextView txtPremiumBadge;
    private LinearLayout uploadArea,extractAppsArea ;
    private ProgressBar progressBar;
    private ImageView imgQrCode;
    private Button btnCopyUrl, btnDeleteAll, btnOpenFolder ;
    private RecyclerView  recyclerClients;


    // Adapters
    private LinearLayout layoutFilesContainer;
    private FileListAdapter fileAdapter;
    private ClientListAdapter clientAdapter;
    //private RecyclerView recyclerFiles;

    // Data
    private List<FileItem> fileList = new ArrayList<>();
    private List<ClientItem> clientList = new ArrayList<>();
    private String currentIp;
    private boolean isPremium = false;
    private String userName;

    // HTTP Client
    private OkHttpClient okHttpClient;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    // File picker
    private ActivityResultLauncher<String> filePickerLauncher;

    private StorageManager storageManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_dashboard);
        storageManager = new StorageManager(this);
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
        updateStorageModeDisplay();

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
        btnOpenFolder = findViewById(R.id.btnOpenFolder);
        recyclerClients = findViewById(R.id.recyclerClients);
        layoutFilesContainer = findViewById(R.id.layoutFilesContainer);
        recyclerClients.setLayoutManager(new LinearLayoutManager(this));
        extractAppsArea = findViewById(R.id.extractAppsArea);
        txtStorageMode = findViewById(R.id.txtStorageMode);

        extractAppsArea.setOnClickListener(v -> {
            Intent intent = new Intent(ServerDashboardActivity.this, AppExtractorActivity.class);
            startActivity(intent);
        });}

    private void loadUserInfo() {
        SharedPreferences prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        userName = prefs.getString("user_name", getUserUniqueName());
        txtUserInfo.setText(userName + " 📱");
    }

    private String getUserUniqueName() {
        SharedPreferences prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        String savedName = prefs.getString("user_name", null);

        if (savedName != null) {
            return savedName;
        }

        String[] persianNames = {"آذرگون", "بارانک", "پونه‌سا", "تیسفون", "جادوک", "چکاد", "خاوران", "دماوند"};
        String[] emojis = {"⚡", "🔥", "💎", "🚀", "⭐", "🌙", "🎯", "💫"};
        String randomName = persianNames[(int)(Math.random() * persianNames.length)];
        String randomEmoji = emojis[(int)(Math.random() * emojis.length)];
        String finalName = randomName + " " + randomEmoji;

        prefs.edit().putString("user_name", finalName).apply();
        return finalName;
    }

    private void loadSettings() {
        SharedPreferences securityPrefs = getSharedPreferences("app_security", MODE_PRIVATE);


        isPremium = BillingManager.getInstance(ServerDashboardActivity.this).isPremiumActivated();

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
        return "0.0.0.0"; // در عمل از MainActivity بگیرید
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
            public void onOpen(String fileName) {
                openFile(fileName);
            }

            @Override
            public void onDelete(String fileName) {
                removeFile(fileName);
            }
        });

        clientAdapter = new ClientListAdapter();
        recyclerClients.setAdapter(clientAdapter);
    }

    private void setupClickListeners() {
        uploadArea.setOnClickListener(v -> openFilePicker());
        btnCopyUrl.setOnClickListener(v -> copyServerUrl());
        btnDeleteAll.setOnClickListener(v -> deleteAllFiles());
        btnOpenFolder.setOnClickListener(v -> openFilesFolder());
        swipeRefresh.setOnRefreshListener(() -> {
            refreshData();
            swipeRefresh.setRefreshing(false);
        });

        txtLocalIp.setOnClickListener(v -> {
            String ip = txtLocalIp.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("IP Address", ip);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "آی‌پی کپی شد", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // دریافت نام فایل از Uri
                        String fileName = getFileNameFromUri(uri);
                        if (fileName == null) {
                            fileName = "file_" + System.currentTimeMillis();
                        }

                        // کپی فایل به پوشه اپلیکیشن
                        copyFileToAppFolder(uri, fileName);
                    }
                }
        );
    }

    private void copyFileToAppFolder(Uri sourceUri, String fileName) {
        if (!isPremium) {
            long fileSize = getFileSize(sourceUri);
            if (fileSize > 500 * 1024 * 1024) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "حجم فایل بیش از 500 مگابایت است. نسخه ویژه را تهیه کنید.", Toast.LENGTH_LONG).show();
                });
                return;
            }
        }

        // استفاده از متد جدید
        File destFolder = getSharedFilesFolder();
        File destFile = new File(destFolder, fileName);

        // اگر فایل قبلاً وجود دارد، نام جدید بساز
        if (destFile.exists()) {
            String nameWithoutExt = fileName;
            String ext = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                nameWithoutExt = fileName.substring(0, dotIndex);
                ext = fileName.substring(dotIndex);
            }
            int counter = 1;
            while (destFile.exists()) {
                String newName = nameWithoutExt + " (" + counter + ")" + ext;
                destFile = new File(destFolder, newName);
                counter++;
            }
        }

        final File finalDestFile = destFile;
        final long totalSize = getFileSize(sourceUri);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            txtUploadProgress.setVisibility(View.VISIBLE);
            txtUploadProgress.setText("در حال کپی فایل: 0%");
        });

        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outputStream = new FileOutputStream(finalDestFile)) {

                byte[] buffer = new byte[131072]; // 128KB بافر برای کپی سریع‌تر
                int length;
                long totalRead = 0;
                int lastProgress = -1;
                long lastTime = System.currentTimeMillis();

                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                    totalRead += length;

                    final int progress = (int) ((totalRead * 100) / totalSize);
                    // آپدیت UI هر 200 میلی‌ثانیه یکبار
                    if (progress != lastProgress && System.currentTimeMillis() - lastTime > 200) {
                        lastProgress = progress;
                        lastTime = System.currentTimeMillis();
                        final int finalProgress = progress;
                        final long finalTotalRead = totalRead;
                        runOnUiThread(() -> {
                            progressBar.setProgress(finalProgress);
                            txtUploadProgress.setText(String.format(Locale.getDefault(),
                                    "در حال کپی: %d%% (%.1f MB / %.1f MB)",
                                    finalProgress, finalTotalRead / (1024.0 * 1024), totalSize / (1024.0 * 1024)));
                        });
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtUploadProgress.setVisibility(View.GONE);
                    //Toast.makeText(this, "✅ فایل به اشتراک گذاشته شد\n" + finalDestFile.getName(), Toast.LENGTH_LONG).show();

                    // افزایش شمارنده آپلود موفق (اگر نیاز داری)
                    incrementUploadCount();

                    // رفرش لیست فایل‌ها
                    refreshData();
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtUploadProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "❌ خطا در کپی فایل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // متد کمکی برای افزایش شمارنده آپلود (مشابه MainActivity)
    private void incrementUploadCount() {
        SharedPreferences prefs = getSharedPreferences("upload_counter", MODE_PRIVATE);
        int count = prefs.getInt("count", 0);
        count++;
        prefs.edit().putInt("count", count).apply();
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
        // دریافت نام کاربر از SharedPreferences
        SharedPreferences prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        String userName = prefs.getString("user_name", getUserUniqueName());
        String userOS = "Android"; // یا هر مقدار دیگری

        // اضافه کردن clientName و clientOS به URL
        String url = "http://" + currentIp + ":8080/api/files?clientName=" + Uri.encode(userName)
                + "&clientOS=" + Uri.encode(userOS) + "&_=" + System.currentTimeMillis();

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

            // اضافه کردن کاربر خودت به لیست (اختیاری)
            boolean isSelfInList = false;
            for (ClientItem client : clientList) {
                if (client.getName().equals(userName)) {
                    client.name+=" (خودم)";
                    isSelfInList = true;
                    break;
                }
            }
            if (!isSelfInList && currentIp != null) {
                clientList.add(0, new ClientItem(userName + " (خودم)", currentIp, "Android"));
            }

            int totalFiles = obj.getInt("totalFiles");
            long totalSize = obj.getLong("totalSize");

            runOnUiThread(() -> {
                displayFiles(new ArrayList<>(fileList));
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
    long totalRead;
    private void uploadFile(Uri uri) {
        // بررسی حجم فایل
        long fileSize = getFileSize(uri);

        if (!isPremium && fileSize > 500 * 1024 * 1024) {
            runOnUiThread(() -> {
                Toast.makeText(this, "حجم فایل بیش از 500 مگابایت است. نسخه ویژه را تهیه کنید.", Toast.LENGTH_LONG).show();
            });
            return;
        }

        String fileName = getFileNameFromUri(uri);
        if (fileName == null) fileName = "file_" + System.currentTimeMillis();
        final String finalFileName = fileName;
        final long totalSize = fileSize;

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(100);
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
                connection.setConnectTimeout(60000);      // 60 ثانیه
                connection.setReadTimeout(600000);        // 10 دقیقه برای فایل بزرگ

                // تنظیم بافر بزرگتر برای فایل‌های بزرگ
                connection.setChunkedStreamingMode(32768); // 32KB chunk size

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

                // نوشتن هدر
                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + finalFileName + "\"" + lineEnd);
                outputStream.writeBytes("Content-Type: application/octet-stream" + lineEnd);
                outputStream.writeBytes(lineEnd);
                outputStream.flush();

                // آپلود با بافر بهینه
                InputStream fileInputStream = getContentResolver().openInputStream(uri);
                byte[] buffer = new byte[65536]; // 64KB بافر (بهتر از 8KB)
                int bytesRead;
                totalRead = 0;
                int lastProgress = -1;
                long lastTime = System.currentTimeMillis();

                // تنظیم timeout برای هر بار خواندن
                if (fileInputStream != null && fileInputStream instanceof java.io.FileInputStream) {
                    java.io.FileInputStream fis = (java.io.FileInputStream) fileInputStream;
                    java.nio.channels.FileChannel channel = fis.getChannel();
                    // استفاده از channel برای خواندن سریع‌تر
                    java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocateDirect(65536);
                    while ((bytesRead = channel.read(byteBuffer)) > 0) {
                        byteBuffer.flip();
                        byte[] chunk = new byte[bytesRead];
                        byteBuffer.get(chunk);
                        outputStream.write(chunk);
                        totalRead += bytesRead;
                        byteBuffer.clear();

                        final int progress = (int) ((totalRead * 100) / totalSize);
                        if (progress != lastProgress && System.currentTimeMillis() - lastTime > 200) {
                            lastProgress = progress;
                            lastTime = System.currentTimeMillis();
                            final int finalProgress = progress;
                            runOnUiThread(() -> {
                                progressBar.setProgress(finalProgress);
                                txtUploadProgress.setText(String.format(Locale.getDefault(), "در حال آپلود: %d%% (%.1f MB / %.1f MB)",
                                        finalProgress, totalRead / (1024.0 * 1024), totalSize / (1024.0 * 1024)));
                            });
                        }
                    }
                    channel.close();
                } else {
                    // Fallback به روش معمولی
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        final int progress = (int) ((totalRead * 100) / totalSize);
                        if (progress != lastProgress && System.currentTimeMillis() - lastTime > 200) {
                            lastProgress = progress;
                            lastTime = System.currentTimeMillis();
                            final int finalProgress = progress;
                            runOnUiThread(() -> {
                                progressBar.setProgress(finalProgress);
                                txtUploadProgress.setText(String.format(Locale.getDefault(), "در حال آپلود: %d%%", finalProgress));
                            });
                        }
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
                        new Handler(Looper.getMainLooper()).postDelayed(() -> refreshData(), 1000);
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


    // متد کمکی برای گرفتن حجم فایل
    private long getFileSize(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            return is.available();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
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
                ImageView imgDelete = itemView.findViewById(R.id.imgDelete);

                txtIcon.setText(getFileIcon(file.getName()));
                txtName.setText(file.getName());
                txtSize.setText(formatSize(file.getSize()));

                final String fileName = file.getName();

                // کلیک روی کل آیتم - باز کردن فایل
                itemView.setOnClickListener(v -> openFile(fileName));

                // کلیک روی آیکون حذف
                imgDelete.setOnClickListener(v -> removeFile(fileName));

                layoutFilesContainer.addView(itemView);
            }
        });
    }


    // متد جدید برای باز کردن/اجرای فایل
    private void openFile(String fileName) {
        File sharedFolder = getSharedFilesFolder();
        File file = new File(sharedFolder, fileName);

        if (!file.exists()) {
            Toast.makeText(this, "فایل وجود ندارد", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);

        String mimeType = getMimeType(fileName);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // برای فایل‌های APK، درخواست مجوز نصب از منابع ناشناس
        if (fileName.endsWith(".apk")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // اندروید 8 به بالا نیاز به مجوز نصب از منابع ناشناس دارد
                if (getPackageManager().canRequestPackageInstalls()) {
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        showInstallPermissionDialog(fileUri);
                    }
                } else {
                    showInstallPermissionDialog(fileUri);
                }
            } else {
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "خطا در باز کردن فایل APK", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            try {
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "برنامه‌ای برای باز کردن این فایل یافت نشد", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showInstallPermissionDialog(Uri apkUri) {
        new AlertDialog.Builder(this)
                .setTitle("📱 نصب برنامه")
                .setMessage("برای نصب این برنامه، نیاز به فعال کردن «نصب از منابع ناشناس» دارید.\n\nآیا می‌خواهید به تنظیمات بروید؟")
                .setPositiveButton("رفتن به تنظیمات", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("بعداً", null)
                .show();
    }

    // متد کمکی برای تشخیص MIME type
    private String getMimeType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);

        switch (extension) {
            // تصاویر
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "webp": return "image/webp";

            // ویدیوها
            case "mp4": return "video/mp4";
            case "mkv": return "video/x-matroska";
            case "avi": return "video/x-msvideo";
            case "3gp": return "video/3gpp";

            // صداها
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "flac": return "audio/flac";
            case "aac": return "audio/aac";

            // اسناد
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt": return "text/plain";

            // آرشیو
            case "zip": return "application/zip";
            case "rar": return "application/x-rar-compressed";
            case "7z": return "application/x-7z-compressed";

            // اپلیکیشن
            case "apk": return "application/vnd.android.package-archive";

            default: return "*/*";
        }
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
        final String[] units = new String[]{"بایت", "کیلوبایت", "مگابایت", "گیگابایت"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void removeFile(String fileName) {
        File sharedFolder = getSharedFilesFolder();
        final File fileToDelete = new File(sharedFolder, fileName);

        // بررسی وجود فایل
        if (!fileToDelete.exists()) {
            Toast.makeText(this, "فایل وجود ندارد", Toast.LENGTH_SHORT).show();
            refreshData();
            return;
        }

        // دیالوگ ساده تأیید (بدون رمز)
        new AlertDialog.Builder(this)
                .setTitle("حذف فایل")
                .setMessage("آیا از حذف فایل \"" + fileName + "\" مطمئن هستید؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    performDirectDelete(fileToDelete, fileName);
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void performDirectDelete(File file, String fileName) {
        new Thread(() -> {
            boolean deleted = file.delete();

            runOnUiThread(() -> {
                if (deleted) {
                    Toast.makeText(this, "🗑️ فایل \"" + fileName + "\" حذف شد", Toast.LENGTH_SHORT).show();
                    refreshData(); // رفرش لیست
                } else {
                    Toast.makeText(this, "خطا در حذف فایل", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // حذف همه فایل‌ها - بدون رمز
    private void deleteAllFiles() {
        File sharedFolder =getSharedFilesFolder();
        File[] files = sharedFolder.listFiles();

        if (files == null || files.length == 0) {
            Toast.makeText(this, "هیچ فایلی برای حذف وجود ندارد", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("حذف همه فایل‌ها")
                .setMessage("آیا از حذف " + files.length + " فایل مطمئن هستید؟")
                .setPositiveButton("حذف همه", (dialog, which) -> {
                    performDirectDeleteAll();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void performDirectDeleteAll() {
        new Thread(() -> {
            File sharedFolder = getSharedFilesFolder();
            File[] files = sharedFolder.listFiles();

            int deletedCount = 0;
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }

            final int finalDeletedCount = deletedCount;
            runOnUiThread(() -> {
                Toast.makeText(this, "🗑️ " + finalDeletedCount + " فایل حذف شد", Toast.LENGTH_SHORT).show();
                refreshData();
            });
        }).start();
    }


    private void openFilesFolder() {
        File sharedFolder = getSharedFilesFolder();

        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs();
            Toast.makeText(this, "پوشه فایل‌ها ایجاد شد", Toast.LENGTH_SHORT).show();
        }

        // روش اول: باز کردن با Intent.ACTION_VIEW
        try {
            // برای اندروید 7 به بالا نیاز به FileProvider داریم
            Uri folderUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                folderUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", sharedFolder);
            } else {
                folderUri = Uri.fromFile(sharedFolder);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();

            // روش دوم: استفاده از Intent.ACTION_OPEN_DOCUMENT_TREE (برای اندروید 5+)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    // اگر اندروید 11+ بود، می‌توانیم مسیر اولیه را تنظیم کنیم
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Uri initialUri = FileProvider.getUriForFile(this,
                                getPackageName() + ".fileprovider", sharedFolder);
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
                    }

                    startActivity(intent);
                } else {
                    // روش سوم: باز کردن با فایل منیجر پیش‌فرض یا نمایش پیام
                    showFolderPathDialog(sharedFolder);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                showFolderPathDialog(sharedFolder);
            }
        }
    }

    private void showFolderPathDialog(File folder) {
        // نمایش مسیر پوشه به کاربر
        String folderPath = folder.getAbsolutePath();

        new AlertDialog.Builder(this)
                .setTitle("📁 مسیر پوشه فایل‌ها")
                .setMessage("فایل‌ها در این مسیر ذخیره می‌شوند:\n\n" + folderPath +
                        "\n\nمی‌توانید با یک فایل منیجر به این مسیر بروید.")
                .setPositiveButton("کپی مسیر", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Folder Path", folderPath);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "مسیر کپی شد", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("باشه", null)
                .show();
    }
    private void updateStorageModeDisplay() {
        if (txtStorageMode != null && storageManager != null) {
            if (storageManager.isUsingPublicDirectory()) {
                txtStorageMode.setText("📁 حالت ذخیره‌سازی: عمومی (Documents/Hamsepar) ✅");
                txtStorageMode.setBackgroundResource(R.drawable.tv_filesdir_bg);
                txtStorageMode.setTextColor(0xFF059669);
                txtStorageMode.setVisibility(View.VISIBLE);
            } else {
                txtStorageMode.setText("📁 حالت ذخیره‌سازی: خصوصی (پوشه برنامه) 🔒");
                txtStorageMode.setBackgroundResource(R.drawable.tv_filesdirprivate_bg);
                txtStorageMode.setTextColor(0xFFD97706);
                txtStorageMode.setVisibility(View.VISIBLE);
            }
        }
    }
    private File getSharedFilesFolder() {

        return storageManager.getStorageDirectory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}