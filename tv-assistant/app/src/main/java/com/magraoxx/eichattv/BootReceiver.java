package com.magraoxx.eichattv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "unknown" : String.valueOf(intent.getAction());
        context.getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_boot_event_at", System.currentTimeMillis())
                .putString("last_boot_action", action)
                .apply();
    }
}
