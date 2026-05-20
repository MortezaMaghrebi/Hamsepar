package com.codestoon.hamsepar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class StoreIntents {


    static void openStoreForComment(Context context) {
        final String packageName = context.getPackageName(); // اسم پکیج همین اپ
        try {
            // باز کردن صفحه‌ی اپ در مایکت
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("myket://comment?id=" + packageName));
            intent.setPackage("ir.mservices.market");
            context.startActivity(intent);
        } catch (Exception e) {
            // اگر مایکت نصب نبود، لینک وبی مایکت باز بشه
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://myket.ir/app/" + packageName));
            context.startActivity(intent);
        }
    }

    static void openDeveloperPage(Context context) {
        final String packageName = context.getPackageName(); // اسم پکیج همین اپ
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("myket://developer/" + packageName));
            intent.setPackage("ir.mservices.market");
            context.startActivity(intent);

        } catch (Exception e) {
            // لینک وب مایکت
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://myket.ir/app/" + packageName));
            context.startActivity(intent);
        }
    }
}
