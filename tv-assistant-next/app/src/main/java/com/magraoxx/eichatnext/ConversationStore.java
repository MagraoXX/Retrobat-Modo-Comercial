package com.magraoxx.eichatnext;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local text history, shared by the screen and the assistant request. */
public final class ConversationStore {
    private final SharedPreferences preferences;
    private JSONArray messages;

    public ConversationStore(Context context) {
        preferences = context.getSharedPreferences("conversation", Context.MODE_PRIVATE);
        try {
            messages = new JSONArray(preferences.getString("messages", "[]"));
        } catch (Exception ignored) {
            messages = new JSONArray();
        }
    }

    public synchronized void append(String role, String text) {
        try {
            messages.put(new JSONObject().put("role", role).put("content", text));
            while (messages.length() > 100) messages.remove(0);
            preferences.edit().putString("messages", messages.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized JSONArray snapshot() {
        try { return new JSONArray(messages.toString()); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public synchronized void clear() {
        messages = new JSONArray();
        preferences.edit().remove("messages").apply();
    }
}