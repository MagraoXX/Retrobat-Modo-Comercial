package com.magraoxx.eichattv;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class StoreController {
    private final Context context;

    public StoreController(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean search(String appName) {
        if (appName == null || appName.trim().isEmpty()) return false;
        String q = URLEncoder.encode(appName.trim(), StandardCharsets.UTF_8);
        Intent market = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=" + q + "&c=apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(market);
            return true;
        } catch (Throwable ignored) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/search?q=" + q + "&c=apps"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(web);
                return true;
            } catch (Throwable ignoredAgain) {
                return false;
            }
        }
    }
}
