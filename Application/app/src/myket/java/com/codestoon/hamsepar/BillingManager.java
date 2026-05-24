package com.codestoon.hamsepar;

import static android.widget.Toast.LENGTH_SHORT;

import android.util.Log;
import android.content.SharedPreferences;
import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import ir.myket.billingclient.IabHelper;
import ir.myket.billingclient.util.IabResult;
import ir.myket.billingclient.util.Inventory;
import ir.myket.billingclient.util.Purchase;

import java.util.ArrayList;
import java.util.List;

public class BillingManager {

    private static final String TAG = "BillingManager";
    private static final String MY_PREFS_NAME = "PREFS_SILENT_SOUND";
    private static final String PREMIUM_KEY = "premium";

    private final Activity activity;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    // ✅ ذخیره payload جاری برای اعتبارسنجی بعدی
    private String currentPayload = null;

    private IabHelper mHelper;
    private boolean mSetupDone = false;

    private static BillingManager instance;

    public static synchronized BillingManager getInstance(Activity activity) {
        if (instance == null) {
            instance = new BillingManager(activity);
        }
        return instance;
    }

    private BillingManager(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences(MY_PREFS_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();

        // ✅ حذف payload ذخیره شده قبلی
        if (prefs.contains("myket_payload")) {
            editor.remove("myket_payload").apply();
            Log.d(TAG, "Removed old stored payload");
        }
    }

    public void initializeBilling() {
        try {
            Log.d(TAG, "Creating Myket IAB helper.");
            mHelper = new IabHelper(activity, BuildConfig.IAB_PUBLIC_KEY);

            mHelper.enableDebugLogging(BuildConfig.ENABLE_DEBUG_LOGS);

            Log.d(TAG, "Starting Myket setup.");
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    Log.d(TAG, "Myket setup finished: " + result);

                    if (mHelper == null) return;

                    if (!result.isSuccess()) {
                        Log.e(TAG, "❌ Problem setting up Myket billing: " + result);
                        return;
                    }

                    mSetupDone = true;
                    Log.d(TAG, "✅ Myket setup successful. Querying inventory.");
                    queryInventory();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing Myket: " + e.getMessage());
        }
    }

    private void queryInventory() {
        if (mHelper == null || !mSetupDone) return;

        try {
            List<String> itemSkus = new ArrayList<>();
            itemSkus.add(PREMIUM_KEY);
            mHelper.queryInventoryAsync(true, itemSkus, mInventoryListener);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error querying inventory: " + e.getMessage());
        }
    }

    IabHelper.QueryInventoryFinishedListener mInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.e(TAG, "❌ Failed to query inventory: " + result);
                return;
            }

            Purchase premiumPurchase = inventory.getPurchase(PREMIUM_KEY);
            boolean hasPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));

            if (hasPremium) {
                Log.d(TAG, "✅ Premium purchase found");
                activatePremiumFeatures();
            } else {
                deactivatePremiumIfNeeded();
            }
        }
    };

    IabHelper.OnIabPurchaseFinishedListener mPurchaseListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.e(TAG, "❌ Purchase failed: " + result);
                Toast.makeText(activity, "❌ پرداخت انجام نشد",LENGTH_SHORT).show();
                return;
            }

            if (purchase == null) {
                Toast.makeText(activity, "❌ خطا در دریافت اطلاعات خرید",LENGTH_SHORT).show();
                return;
            }

            // ✅ اعتبارسنجی دقیق‌تر payload
            if (!verifyDeveloperPayload(purchase)) {
                Log.e(TAG, "⚠️ Payload verification failed! Possible hijack attempt.");
                Toast.makeText(activity, "❌ خطا در تأیید اعتبار خرید",LENGTH_SHORT).show();
                return;
            }

            if (!purchase.getSku().equals(PREMIUM_KEY)) {
                Log.e(TAG, "⚠️ SKU mismatch: " + purchase.getSku());
                Toast.makeText(activity, "❌ خطا در تطابق محصول",LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "✅ Purchase successful");
            activatePremiumFeatures();
            Toast.makeText(activity, "✅ پرداخت با موفقیت انجام شد",LENGTH_SHORT).show();

            // ✅ پاک کردن payload جاری بعد از خرید موفق
            currentPayload = null;
        }
    };

    public void purchasePremium() {
        if (mHelper == null || !mSetupDone) {
            Toast.makeText(activity, "❌ سرویس پرداخت آماده نیست",LENGTH_SHORT).show();
            return;
        }

        try {
            // ✅ تولید payload جدید و یکتا برای هر خرید
            String payload = generateUniquePayload();
            currentPayload = payload;  // ذخیره برای اعتبارسنجی بعدی
            Log.d(TAG, "🆕 Generated new payload for Myket: " + payload);

            mHelper.launchPurchaseFlow(activity, PREMIUM_KEY, mPurchaseListener, payload);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting purchase: " + e.getMessage());
            Toast.makeText(activity, "❌ خطا در شروع فرآیند پرداخت",LENGTH_SHORT).show();
            currentPayload = null;
        }
    }

    // ✅ متد جدید برای تولید payload یکتا
    private String generateUniquePayload() {
        // ترکیب timestamp و UUID برای یکتایی بیشتر
        return System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString();
    }

    private void activatePremiumFeatures() {
        editor.putBoolean("premium_activated", true);
        editor.apply();
        //updateAppData();
    }

    private void deactivatePremiumIfNeeded() {
        if (prefs.getBoolean("premium_activated", false)) {
            editor.putBoolean("premium_activated", false);
            editor.apply();
        }
    }

    private void updateAppData() {
        try {
            editor.putInt("download_sound_counter", 2);
            editor.apply();  // ✅ replace commit() with apply() for better performance
            // NetController.getInstance(activity).DownloadSoundList();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating app data: " + e.getMessage());
        }
    }

    // ✅ بهبود اعتبارسنجی payload
    private boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        if (payload == null || payload.isEmpty()) {
            Log.e(TAG, "❌ Empty or null payload in purchase");
            return false;
        }

        // ✅ بررسی اینکه payload با payload جاری مطابقت دارد
        if (currentPayload != null && !payload.equals(currentPayload)) {
            Log.e(TAG, "❌ Payload mismatch! Expected: " + currentPayload + ", Got: " + payload);
            return false;
        }

        Log.d(TAG, "✅ Payload verified successfully");
        return true;
    }

    public boolean isPremiumActivated() {
        return prefs.getBoolean("premium_activated", false);
    }

    public boolean isReady() {
        return mHelper != null && mSetupDone;
    }

    public void disconnect() {
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
        mSetupDone = false;
        currentPayload = null;
    }

    public void onDestroy() {
        disconnect();
    }
}