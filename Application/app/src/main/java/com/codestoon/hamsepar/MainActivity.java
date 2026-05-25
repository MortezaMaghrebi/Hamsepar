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
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {

    private BillingManager billingManager;
    private static final int PORT = 8080;
    private static final long MAX_FREE_SIZE_BYTES = 500 * 1024 * 1024;
    private static final String PREF_UPLOAD_COUNT = "upload_success_count";
    private static final String PREF_RATING_DIALOG_SHOWN = "rating_dialog_shown";
    private static final String PREF_RATING_DIALOG_LAST_TIME = "rating_dialog_last_time";
    private static final int PROMPT_COMMENT_THRESHOLD = 2;
    public static String CURRENT_SERVER_IP = "";
    private static final ConcurrentHashMap<String, ClientInfo> connectedClients = new ConcurrentHashMap<>();

    private static class ClientInfo {
        String name;
        String os;
        long lastSeen;
        ClientInfo(String name, String os) {
            this.name = name;
            this.os = os;
            this.lastSeen = System.currentTimeMillis();
        }
        void updateSeen() { this.lastSeen = System.currentTimeMillis(); }
    }

    // UI Components
    private TextView txtStatus, txtServerUrl, txtLocalIp, txtClientsCount, txtStorageMode;
    private Button btnStartStop, btnOpenBrowser, btnCopyUrl;
    private androidx.appcompat.widget.Toolbar toolbar;
    private android.widget.ImageView imgQrCode;
    private android.view.View indicatorStatus;
    private CheckBox chkProtectDelete;

    // Managers
    private FileServer webServer;
    private String currentIp;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean deleteProtectionEnabled = false;
    private String deletePassword = "";
    private StorageManager storageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        // مقداردهی StorageManager
        storageManager = new StorageManager(this);

        // بررسی دسترسی‌های حافظه با کد خودت
        takePermissions();

        initBilling();

        prefs = getSharedPreferences("app_security", MODE_PRIVATE);
        deleteProtectionEnabled = prefs.getBoolean("protect_delete", false);
        deletePassword = prefs.getString("delete_password", "5949");
        chkProtectDelete.setChecked(deleteProtectionEnabled);

        chkProtectDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            deleteProtectionEnabled = isChecked;
            prefs.edit().putBoolean("protect_delete", isChecked).apply();
        });
    }

    private void initViews() {
        txtStatus = findViewById(R.id.txtServerStatus);
        txtServerUrl = findViewById(R.id.txtServerUrl);
        txtLocalIp = findViewById(R.id.txtLocalIp);
        txtClientsCount = findViewById(R.id.txtClientsCount);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser);
        btnCopyUrl = findViewById(R.id.btnCopyUrl);
        imgQrCode = findViewById(R.id.imgQrCode);
        indicatorStatus = findViewById(R.id.indicatorStatus);
        chkProtectDelete = findViewById(R.id.chkProtectDelete);
        txtStorageMode = findViewById(R.id.txtStorageMode);
    }

    private void setupListeners() {
        btnStartStop.setOnClickListener(v -> {
            if (webServer == null) startServer();
            else stopServer();
        });

        btnOpenBrowser.setOnClickListener(v -> {
            if (webServer != null && currentIp != null) {
                String url = "http://" + currentIp + ":" + PORT;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else {
                Toast.makeText(this, "سرور روشن نیست", Toast.LENGTH_SHORT).show();
            }
        });

        btnCopyUrl.setOnClickListener(v -> {
            if (webServer != null && currentIp != null) {
                String url = "http://" + currentIp + ":" + PORT;
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Server URL", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "لینک کپی شد", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "سرور روشن نیست", Toast.LENGTH_SHORT).show();
            }
        });

        initFooterButtons();
    }

    // ==================== FILE PERMISSIONS (کد خودت) ====================

    private void takePermissions() {
        if (isPermissionGranted()) {
            updateStorageModeDisplay();
        } else {
            showStorageExplanationDialog();
        }
    }

    private boolean isPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int readExternalStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            return readExternalStoragePermission == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void takePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 6100);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 6100);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 6101);
        }
    }

    private void showStorageExplanationDialog() {
        String githubLink = "https://github.com/mortezamaghrebi/Hamsepar";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            takePermission();
        });
        builder.setNeutralButton("🛡️ دسترسی محدود", (dialog, which) -> {
            storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
            storageManager.setFullAccessGranted(false);
            updateStorageModeDisplay();
            Toast.makeText(this, "📁 فایل‌ها در پوشه خصوصی برنامه ذخیره می‌شوند", Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("❌ بعداً", (dialog, which) -> {
            Toast.makeText(this, "برای استفاده از برنامه بعداً دسترسی را فعال کنید", Toast.LENGTH_LONG).show();
        });
        builder.setCancelable(false);
        builder.show();
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

    // ==================== بقیه کدها ====================

    private void initBilling() {
        billingManager = BillingManager.getInstance(this);
        billingManager.initializeBilling();
    }

    private void showRatingDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (prefs.getBoolean(PREF_RATING_DIALOG_SHOWN, false)) return;

        long lastDialogTime = prefs.getLong(PREF_RATING_DIALOG_LAST_TIME, 0);
        long currentTime = System.currentTimeMillis();
        long twentyFourHoursInMillis = 24 * 60 * 60 * 1000;

        if (lastDialogTime > 0 && (currentTime - lastDialogTime) < twentyFourHoursInMillis) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("✨ تجربه خوبی داشتید؟")
                .setMessage("از اینکه از همسپار استفاده می‌کنید خوشحالیم. آیا مایل به ثبت نظر و حمایت از ما در بازار هستید؟")
                .setPositiveButton("👍 بله، نظر میدم", (dialog, which) -> {
                    try {
                        StoreIntents.openStoreForComment(MainActivity.this);
                        prefs.edit().putBoolean(PREF_RATING_DIALOG_SHOWN, true).apply();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "خطا در باز کردن بازار", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("🙏 بعداً یادآوری کن", (dialog, which) -> {
                    prefs.edit().putLong(PREF_RATING_DIALOG_LAST_TIME, System.currentTimeMillis()).apply();
                })
                .setCancelable(true)
                .show();
    }

    private void incrementUploadCounter() {
        int count = prefs.getInt(PREF_UPLOAD_COUNT, 0);
        count++;
        prefs.edit().putInt(PREF_UPLOAD_COUNT, count).apply();

        if (count >= PROMPT_COMMENT_THRESHOLD && billingManager != null && !billingManager.isPremiumActivated()) {
            showRatingDialog();
        }
    }

    public void restartServerAfterPurchase() {
        if (webServer != null) {
            stopServer();
            new Handler(Looper.getMainLooper()).postDelayed(this::startServer, 500);
        }
    }

    void initFooterButtons() {
        View browserButton = findViewById(R.id.browserButton);
        if (browserButton != null) {
            browserButton.setOnClickListener(v -> {
                animateButton(v);
                openBrowser();
            });
        }

        View commentBtn = findViewById(R.id.commentButton);
        if (commentBtn != null) {
            commentBtn.setOnClickListener(v -> {
                animateButton(v);
                Intent intent = new Intent(MainActivity.this, CommentActivity.class);
                startActivity(intent);
            });
        }

        View premiumBtn = findViewById(R.id.premiumButton);
        if (premiumBtn != null) {
            premiumBtn.setOnClickListener(v -> {
                animateButton(v);
                showPremiumPurchaseDialog();
            });
        }

        View otherAppsBtn = findViewById(R.id.otherAppsButton);
        if (otherAppsBtn != null) {
            otherAppsBtn.setOnClickListener(v -> {
                animateButton(v);
                StoreIntents.openDeveloperPage(MainActivity.this);
            });
        }

        FloatingActionButton fabComment = findViewById(R.id.fabComment);
        if (fabComment != null) {
            fabComment.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CommentActivity.class);
                startActivity(intent);
            });
        }
    }

    private void animateButton(View button) {
        button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    private void showPremiumPurchaseDialog() {
        if (billingManager != null && billingManager.isPremiumActivated()) {
            new AlertDialog.Builder(this)
                    .setTitle("🎁 شما کاربر ویژه هستید!")
                    .setMessage("با تشکر از حمایت شما، می‌توانید فایل‌های با هر حجمی را انتقال دهید.\n\n")
                    .setPositiveButton("باشه 😊", null)
                    .show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_premium, null);

        TextView tvPrice = view.findViewById(R.id.tvPrice);
        Button btnBuy = view.findViewById(R.id.btnBuy);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        tvPrice.setText("39,000 تومان");

        builder.setView(view);
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();

        btnBuy.setOnClickListener(v -> {
            if (billingManager != null && billingManager.isReady()) {
                billingManager.purchasePremium();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "⏳ سرویس پرداخت در حال آماده‌سازی...", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openBrowser() {
        if (webServer != null && currentIp != null) {
            Intent intent = new Intent(MainActivity.this, ServerDashboardActivity.class);
            startActivity(intent);
        } else if (currentIp != null) {
            Intent intent = new Intent(MainActivity.this, ServerDashboardActivity.class);
            startActivity(intent);
            Toast.makeText(this, "سرور خاموش است. ابتدا سرور را روشن کنید.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "ابتدا سرور را روشن کنید.", Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> getAllLocalIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.equals("0.0.0.0") && !ip.startsWith("127."))
                            ips.add(ip);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        if (ips.isEmpty()) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) ips.add(Formatter.formatIpAddress(ipInt));
            }
        }
        return ips;
    }

    private void startServer() {
        List<String> ips = getAllLocalIps();
        if (ips.isEmpty()) {
            Toast.makeText(this, "خطا: آی‌پی پیدا نشد! وای‌فای یا هات‌اسپات روشن است؟", Toast.LENGTH_LONG).show();
            showManualIpHelp();
            return;
        }
        if (ips.size() > 1) {
            chooseIpFromList(ips);
        } else {
            currentIp = ips.get(0);
            startServerWithIp(currentIp);
        }
    }

    AlertDialog ip_dialog;
    private void chooseIpFromList(List<String> ips) {
        List<IpInfo> ipInfoList = new ArrayList<>();
        for (String ip : ips) {
            ipInfoList.add(new IpInfo(ip, detectIpType(ip), getIpIcon(ip)));
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ip_selector, null);
        RecyclerView recyclerIps = dialogView.findViewById(R.id.recyclerIps);
        recyclerIps.setLayoutManager(new LinearLayoutManager(this));

        IpSelectorAdapter adapter = new IpSelectorAdapter(ipInfoList, ipInfo -> {
            currentIp = ipInfo.ip;
            ip_dialog.dismiss();
            startServerWithIp(currentIp);
        });
        recyclerIps.setAdapter(adapter);

        ip_dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("راهنما", (d, w) -> showManualIpHelp())
                .setPositiveButton("اتصال خودکار", (d, w) -> {
                    String selectedIp = null;
                    for (IpInfo info : ipInfoList) {
                        if (info.type.contains("هات‌اسپات") || info.type.contains("وای‌فای")) {
                            selectedIp = info.ip;
                            break;
                        }
                    }
                    if (selectedIp == null && !ipInfoList.isEmpty()) {
                        selectedIp = ipInfoList.get(0).ip;
                    }
                    if (selectedIp != null) {
                        currentIp = selectedIp;
                        startServerWithIp(currentIp);
                    }
                })
                .create();
        ip_dialog.show();
    }

    private String getIpIcon(String ip) {
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return "📶";
        } else if (ip.startsWith("172.")) {
            return "🌐";
        } else if (ip.startsWith("169.254.")) {
            return "⚠️";
        }
        return "📡";
    }

    private String detectIpType(String ip) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getHostAddress().equals(ip)) {
                        String ifaceName = iface.getDisplayName().toLowerCase();
                        if (ifaceName.contains("wlan") || ifaceName.contains("wifi")) {
                            return "📶 وای‌فای (WiFi) - مناسب برای اشتراک‌گذاری";
                        } else if (ifaceName.contains("p2p") || ifaceName.contains("softap")) {
                            return "🔥 هات‌اسپات (Hotspot) - مناسب برای اشتراک‌گذاری";
                        } else if (ifaceName.contains("rmnet") || ifaceName.contains("mobile") || ifaceName.contains("cell")) {
                            return "📱 شبکه همراه (Mobile) - ممکن است کار نکند";
                        } else if (ifaceName.contains("eth") || ifaceName.contains("ethernet")) {
                            return "🔌 اترنت (Ethernet) - مناسب برای اشتراک‌گذاری";
                        } else if (ifaceName.contains("usb")) {
                            return "🔗 اتصال USB - مناسب است";
                        } else if (ifaceName.contains("bluetooth")) {
                            return "🎧 بلوتوث (Bluetooth) - سرعت پایین";
                        } else if (ifaceName.contains("loopback") || ip.equals("127.0.0.1")) {
                            return "🔄 محلی (Local) - فقط برای خود دستگاه";
                        } else {
                            return "❓ نوع ناشناس - امتحان کنید";
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
            return "🌐 شبکه محلی (LAN) - مناسب برای اشتراک‌گذاری";
        } else if (ip.startsWith("169.254.")) {
            return "⚠️ IP خودکار (APIPA) - ممکن است کار نکند";
        } else {
            return "❓ آی‌پی ناشناس - امتحان کنید";
        }
    }

    private static class IpInfo {
        String ip;
        String type;
        String icon;
        IpInfo(String ip, String type, String icon) {
            this.ip = ip;
            this.type = type;
            this.icon = icon;
        }
    }

    private interface OnIpSelectedListener {
        void onSelected(IpInfo ipInfo);
    }

    private class IpSelectorAdapter extends RecyclerView.Adapter<IpSelectorAdapter.ViewHolder> {
        private List<IpInfo> ipList;
        private OnIpSelectedListener listener;

        IpSelectorAdapter(List<IpInfo> ipList, OnIpSelectedListener listener) {
            this.ipList = ipList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ip_selector, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IpInfo info = ipList.get(position);
            holder.txtIcon.setText(info.icon);
            holder.txtIpAddress.setText(info.ip);
            holder.txtIpType.setText(info.type);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSelected(info);
            });
        }

        @Override
        public int getItemCount() {
            return ipList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtIcon, txtIpAddress, txtIpType;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtIcon = itemView.findViewById(R.id.txtIpIcon);
                txtIpAddress = itemView.findViewById(R.id.txtIpAddress);
                txtIpType = itemView.findViewById(R.id.txtIpType);
            }
        }
    }

    private void showManualIpHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔍 راهنمای پیدا کردن آی‌پی");

        // سوال اول: آیا هات‌اسپات روشن است؟
        builder.setMessage("⚠️ قبل از ادامه، مطمئن شوید:\n\n" +
                "✅ هات‌اسپات گوشی روشن باشد\n" +
                "✅ دستگاه دیگر به این هات‌اسپات متصل شده باشد\n" +
                "✅ یا هر دو دستگاه به یک وای‌فای مشترک متصل باشند\n\n" +
                "آیا هات‌اسپات را روشن کرده‌اید؟");

        builder.setPositiveButton("✅ بله، روشن است", (dialog, which) -> {
            // اگر کاربر گفت بله، راهنمای کامل را نشان بده
            showFullIpHelp();
        });

        builder.setNegativeButton("❌ نه، بعداً", (dialog, which) -> {
            Toast.makeText(this, "لطفاً ابتدا هات‌اسپات را روشن کنید و دوباره تلاش کنید", Toast.LENGTH_LONG).show();
        });

        builder.setNeutralButton("📖 راهنمای ویدیویی", (dialog, which) -> {
            // باز کردن یک صفحه راهنما یا ویدیو (اختیاری)
            openVideoGuide();
            //showVisualGuideDialog();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showFullIpHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔍 راهنمای پیدا کردن آی‌پی");

        String message = "1️⃣ به تنظیمات وای‌فای گوشی بروید.\n\n" +
                "2️⃣ روی شبکه متصل شده ضربه بزنید.\n\n" +
                "3️⃣ گزینه «جزئیات شبکه» یا «آی‌پی» را ببینید.\n\n" +
                "4️⃣ اگر چند آی‌پی می‌بینید، یکی یکی امتحان کنید تا ببینید کدام کار می‌کند.\n\n" +
                "5️⃣ اگر باز هم پیدا نکردید:\n" +
                "   • از دستگاه دیگر (گیرنده) هات‌اسپات بدهید\n" +
                "   • این دستگاه (دهنده) به هات‌اسپات وصل شود\n" +
                "   • دوباره شروع سرور را بزنید\n\n" +
                "💡 نکته: معمولاً آی‌پی‌های 192.168.x.x یا 10.x.x.x کار می‌کنند.";

        builder.setMessage(message);
        builder.setPositiveButton("🙏 متشکرم", null);
        builder.setNeutralButton("🔄 دوباره تلاش کن", (dialog, which) -> {
            // بازگشت به صفحه اصلی برای تلاش مجدد
            startServer();
        });
        builder.show();
    }

    private void showVisualGuideDialog() {
        List<GuidePagerAdapter.GuideItem> guideItems = new ArrayList<>();

        // صفحه 1 - تنظیمات وای‌فای
        guideItems.add(new GuidePagerAdapter.GuideItem(
                "📱⚙️",
                "📍 مرحله 1: تنظیمات وای‌فای",
                "به بخش «تنظیمات» گوشی بروید و روی گزینه «وای‌فای» یا «شبکه و اینترنت» ضربه بزنید."
        ));

        // صفحه 2 - شبکه متصل شده
        guideItems.add(new GuidePagerAdapter.GuideItem(
                "📶✅",
                "📍 مرحله 2: شبکه متصل شده",
                "روی شبکه وای‌فای یا هات‌اسپاتی که به آن متصل هستید (که معمولاً علامت ✅ دارد) ضربه بزنید."
        ));

        // صفحه 3 - جزئیات شبکه
        guideItems.add(new GuidePagerAdapter.GuideItem(
                "🔍📡",
                "📍 مرحله 3: جزئیات شبکه",
                "گزینه «جزئیات شبکه» یا «آی‌پی» را پیدا کنید. آی‌پی نوشته شده (مثلاً 192.168.43.1) را یادداشت کنید."
        ));

        // صفحه 4 - راهکار جایگزین
        guideItems.add(new GuidePagerAdapter.GuideItem(
                "🔄💡",
                "💡 راهکار جایگزین",
                "اگر آی‌پی پیدا نکردید:\n• از دستگاه دیگر هات‌اسپات بدهید\n• این دستگاه به آن وصل شود\n• دوباره شروع سرور را بزنید"
        ));

        // ایجاد دیالوگ
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_visual_guide, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerGuide);
        TabLayout tabLayout = view.findViewById(R.id.tabLayoutIndicator);
        Button btnPrev = view.findViewById(R.id.btnPrev);
        Button btnNext = view.findViewById(R.id.btnNext);

        GuidePagerAdapter adapter = new GuidePagerAdapter(guideItems);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                btnPrev.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                btnNext.setText(position == adapter.getItemCount() - 1 ? "✅ تمام" : "بعدی ▶");
            }
        });

        btnPrev.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() - 1));
        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() == adapter.getItemCount() - 1) {
                dialog.dismiss();
            } else {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            }
        });

        dialog.show();
    }

    // در متد showManualIpHelp یا هر جای دیگر
    private void openVideoGuide() {
        Intent intent = new Intent(MainActivity.this, VideoGuideActivity.class);
        startActivity(intent);
    }

    private void startServerWithIp(String ip) {
        currentIp = ip;
        CURRENT_SERVER_IP = ip;
        txtLocalIp.setText(currentIp);
        String url = "http://" + currentIp + ":" + PORT;
        txtServerUrl.setText(url);
        generateQrCode(url);

        try {
            File storageDir = storageManager.getStorageDirectory();
            boolean isUserPremium = (billingManager != null && billingManager.isPremiumActivated());
            webServer = new FileServer(PORT, storageDir, deleteProtectionEnabled, deletePassword, currentIp, isUserPremium);
            webServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            updateServerUi(true);

            openBrowser();
            mainHandler.postDelayed(clientUpdater, 2000);

            String message = storageManager.isUsingPublicDirectory() ?
                    "📁 فایل‌ها در پوشه Documents/Hamsepar ذخیره می‌شوند" :
                    "📁 فایل‌ها در پوشه خصوصی برنامه ذخیره می‌شوند (با فایل منیجر قابل مشاهده نیست)";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "خطا در شروع سرور", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
            updateServerUi(false);
            Toast.makeText(this, "سرور متوقف شد", Toast.LENGTH_SHORT).show();
            mainHandler.removeCallbacks(clientUpdater);
            connectedClients.clear();
            txtClientsCount.setText("0");
        }
    }

    private void updateServerUi(boolean isRunning) {
        if (isRunning) {
            txtStatus.setText("فعال ✅");
            btnStartStop.setText("توقف سرور");
            indicatorStatus.setBackgroundResource(R.drawable.indicator_on);
            btnOpenBrowser.setEnabled(true);
            btnCopyUrl.setEnabled(true);
        } else {
            txtStatus.setText("خاموش ❌");
            btnStartStop.setText("شروع سرور");
            indicatorStatus.setBackgroundResource(R.drawable.indicator_off);
            txtServerUrl.setText("---");
            btnOpenBrowser.setEnabled(false);
            btnCopyUrl.setEnabled(false);
        }
    }

    private void generateQrCode(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
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

    private void updateClientsDisplay() {
        mainHandler.post(() -> {
            long now = System.currentTimeMillis();
            connectedClients.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 10000);
            txtClientsCount.setText(String.valueOf(connectedClients.size()));
        });
    }

    private Runnable clientUpdater = new Runnable() {
        @Override
        public void run() {
            if (webServer != null) {
                updateClientsDisplay();
                mainHandler.postDelayed(this, 2000);
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 6101: { // FOR FILE PERMISSION (اندروید 10 و پایین‌تر)
                if (grantResults.length > 0) {
                    boolean readExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (readExternalStorage) {
                        storageManager.setStorageMode(StorageManager.MODE_FULL_ACCESS);
                        storageManager.setFullAccessGranted(true);
                        updateStorageModeDisplay();
                        Toast.makeText(this, "✅ دسترسی به حافظه داده شد", Toast.LENGTH_SHORT).show();
                    } else {
                        storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
                        storageManager.setFullAccessGranted(false);
                        updateStorageModeDisplay();
                        Toast.makeText(this, "⚠️ برنامه با دسترسی محدود ادامه می‌دهد", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 6100) {
            if (isPermissionGranted()) {
                storageManager.setStorageMode(StorageManager.MODE_FULL_ACCESS);
                storageManager.setFullAccessGranted(true);
                updateStorageModeDisplay();
                Toast.makeText(this, "✅ دسترسی کامل به حافظه داده شد", Toast.LENGTH_SHORT).show();
            } else {
                storageManager.setStorageMode(StorageManager.MODE_PRIVATE_ACCESS);
                storageManager.setFullAccessGranted(false);
                updateStorageModeDisplay();
                Toast.makeText(this, "⚠️ دسترسی کامل داده نشد. برنامه با دسترسی محدود ادامه می‌دهد.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStorageModeDisplay();
    }

    // ==================== کلاس سرور داخلی ====================

    private class FileServer extends NanoHTTPD {
        private final File rootDir;
        private final boolean protectDelete;
        private final String password;
        private final String serverIp;
        private final boolean isPremium;

        public FileServer(int port, File storageDir, boolean protectDelete, String password, String serverIp, boolean isPremium) {
            super(port);
            this.rootDir = storageDir;
            this.protectDelete = protectDelete;
            this.password = password;
            this.serverIp = serverIp;
            this.isPremium = isPremium;
        }

        private Response newBinaryResponse(Response.Status status, String mimeType, byte[] data) {
            return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(data), data.length);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            String ip = session.getRemoteIpAddress();

            Map<String, String> params = session.getParms();
            String clientName = params.get("clientName");
            String clientOS = params.get("clientOS");

            if (clientName != null && !clientName.isEmpty()) {
                ClientInfo info = connectedClients.get(ip);
                if (info == null) {
                    info = new ClientInfo(clientName, clientOS != null ? clientOS : "ناشناس");
                    connectedClients.put(ip, info);
                } else {
                    info.name = clientName;
                    if (clientOS != null) info.os = clientOS;
                    info.updateSeen();
                }
            } else {
                ClientInfo info = connectedClients.get(ip);
                if (info != null) {
                    info.updateSeen();
                } else {
                    connectedClients.put(ip, new ClientInfo("کاربر " + ip, "ناشناس"));
                }
            }

            if ("/qrcode.png".equals(uri)) {
                String fullUrl = "http://" + serverIp + ":" + PORT;
                Bitmap qrBitmap = generateQrBitmap(fullUrl, 200, 200);
                if (qrBitmap != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    byte[] imageBytes = baos.toByteArray();
                    return newBinaryResponse(Response.Status.OK, "image/png", imageBytes);
                } else {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "QR generation failed");
                }
            }

            if ("/api/files".equals(uri)) {
                String json = getFilesAndClientsJson();
                return newFixedLengthResponse(Response.Status.OK, "application/json", json);
            }

            if (uri.startsWith("/download")) {
                Map<String, String> params2 = session.getParms();
                String fileName = params2.get("file");
                if (fileName != null) {
                    File file = new File(rootDir, fileName);
                    if (file.exists() && !file.isDirectory()) {
                        try {
                            FileInputStream fis = new FileInputStream(file);
                            Response res = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, (int) file.length());
                            res.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                            return res;
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "فایل یافت نشد");
            }

            if ("/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
                try {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    String tempFilePath = files.get("file");
                    if (tempFilePath != null) {
                        File tempFile = new File(tempFilePath);

                        if (!isPremium && tempFile.length() > MAX_FREE_SIZE_BYTES) {
                            tempFile.delete();
                            return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json",
                                    "{\"error\":\"حجم فایل بیش از 500 مگابایت است. برای آپلود فایل‌های بزرگتر، نسخه ویژه را تهیه کنید.\"}");
                        }

                        String originalFileName = session.getParms().get("file");
                        if (originalFileName == null || originalFileName.isEmpty())
                            originalFileName = tempFile.getName();
                        File destFile = new File(rootDir, originalFileName);
                        if (tempFile.renameTo(destFile)) {
                            runOnUiThread(() -> incrementUploadCounter());
                            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
                        } else {
                            try (FileInputStream fis = new FileInputStream(tempFile);
                                 FileOutputStream fos = new FileOutputStream(destFile)) {
                                byte[] buffer = new byte[8192];
                                int length;
                                while ((length = fis.read(buffer)) > 0) fos.write(buffer, 0, length);
                            }
                            tempFile.delete();
                            runOnUiThread(() -> incrementUploadCounter());
                            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "آپلود ناموفق");
            }

            if ("/delete".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> params2 = session.getParms();
                String fileName = params2.get("fileName");

                if (fileName == null || fileName.isEmpty()) {
                    try {
                        Map<String, String> files = new HashMap<>();
                        session.parseBody(files);
                        if (files.containsKey("fileName")) {
                            fileName = files.get("fileName");
                        }
                        Map<String, String> bodyParams = session.getParms();
                        if (fileName == null && bodyParams.containsKey("fileName")) {
                            fileName = bodyParams.get("fileName");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String providedPass = params2.get("password");
                if (providedPass == null) {
                    try {
                        Map<String, String> files = new HashMap<>();
                        session.parseBody(files);
                        if (files.containsKey("password")) {
                            providedPass = files.get("password");
                        }
                    } catch (Exception e) {}
                }

                if (protectDelete && (providedPass == null || !providedPass.equals(password))) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\":\"رمز اشتباه است\"}");
                }
                if (fileName != null && !fileName.isEmpty()) {
                    File fileToDelete = new File(rootDir, fileName);
                    if (fileToDelete.exists()) {
                        fileToDelete.delete();
                        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
                    } else {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"فایل یافت نشد\"}");
                    }
                }
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"نام فایل ارسال نشده\"}");
            }

            if ("/delete-all".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> params2 = session.getParms();
                String providedPass = params2.get("password");
                if (protectDelete && (providedPass == null || !providedPass.equals(password))) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\":\"رمز اشتباه است\"}");
                }
                File[] files = rootDir.listFiles();
                if (files != null) for (File f : files) f.delete();
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                String html = loadAsset("index.html");
                if (html != null) {
                    String packageName = getPackageName();
                    String storeName = BuildConfig.STORE_NAME;

                    String storeLink;
                    String storeDisplayName;
                    String storeColor;

                    if ("myket".equals(storeName)) {
                        storeLink = "https://myket.ir/app/" + packageName;
                        storeDisplayName = "مایکت";
                        storeColor = "#3b82f6";
                    } else {
                        storeLink = "https://cafebazaar.ir/app/" + packageName;
                        storeDisplayName = "بازار";
                        storeColor = "#10b981";
                    }

                    html = html.replace("{{STORE_LINK}}", storeLink);
                    html = html.replace("{{STORE_NAME}}", storeDisplayName);
                    html = html.replace("{{STORE_COLOR}}", storeColor);

                    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
                } else {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "خطا در بارگذاری صفحه");
                }
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }

        private Bitmap generateQrBitmap(String text, int width, int height) {
            try {
                BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                    }
                }
                return bitmap;
            } catch (WriterException e) {
                e.printStackTrace();
                return null;
            }
        }

        private String getFilesAndClientsJson() {
            StringBuilder sb = new StringBuilder("{\"files\":[");
            File[] files = rootDir.listFiles();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            long totalSize = 0;
            int fileCount = 0;
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("{")
                            .append("\"name\":\"").append(escapeJson(files[i].getName())).append("\",")
                            .append("\"size\":").append(files[i].length()).append(",")
                            .append("\"modified\":\"").append(sdf.format(new Date(files[i].lastModified()))).append("\"")
                            .append("}");
                    totalSize += files[i].length();
                    fileCount++;
                }
            }
            sb.append("],\"clients\":[");
            boolean first = true;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, ClientInfo> entry : connectedClients.entrySet()) {
                if (now - entry.getValue().lastSeen > 10000) continue;
                if (!first) sb.append(",");
                sb.append("{")
                        .append("\"name\":\"").append(escapeJson(entry.getValue().name)).append("\",")
                        .append("\"ip\":\"").append(entry.getKey()).append("\",")
                        .append("\"os\":\"").append(escapeJson(entry.getValue().os)).append("\"")
                        .append("}");
                first = false;
            }
            sb.append("],\"totalFiles\":").append(fileCount);
            sb.append(",\"totalSize\":").append(totalSize);
            sb.append(",\"protectDelete\":").append(protectDelete);
            sb.append(",\"isPremium\":").append(isPremium);
            sb.append("}");
            return sb.toString();
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }

        private String loadAsset(String filename) {
            try (InputStream is = getAssets().open(filename)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                return baos.toString("UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}