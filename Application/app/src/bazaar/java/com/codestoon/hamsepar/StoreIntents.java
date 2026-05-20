package com.codestoon.hamsepar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class StoreIntents {


    static void openStoreForComment(Context context) {
		final String packageName = context.getPackageName(); // اسم پکیج همین اپ
        try {
            // باز کردن صفحه‌ی اپ در بازار (بخش کامنت و امتیاز)
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setData(Uri.parse("bazaar://details?id=" + packageName));
            intent.setPackage("com.farsitel.bazaar");
            context.startActivity(intent);
        } catch (Exception e) {
            // اگر بازار نصب نبود، لینک وبی بازار باز بشه
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://cafebazaar.ir/app/" + packageName));
            context.startActivity(intent);
        }
    }

    // نمایش سایر برنامه‌های توسعه‌دهنده در بازار
    static void openDeveloperPage(Context context) {
        final String developerName = "326890183011"; // نام توسعه‌دهنده در بازار (تغییر دهید)
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("bazaar://collection?slug=by_author&aid=" + developerName));
            intent.setPackage("com.farsitel.bazaar");
            context.startActivity(intent);

        } catch (Exception e) {
            // لینک وب بازار
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://cafebazaar.ir/developer/" + developerName));
            context.startActivity(intent);
        }
    }


}
