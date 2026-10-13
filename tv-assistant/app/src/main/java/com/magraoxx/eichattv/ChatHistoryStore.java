package com.magraoxx.eichattv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ChatHistoryStore {
    public static final class Message {
        public final String role;
        public final String text;
        public Message(String role, String text) { this.role = role; this.text = text; }
    }

    public static final class Conversation {
        public final String id;
        public String title;
        public long updatedAt;
        public final ArrayList<Message> messages = new ArrayList<>();
        Conversation(String id, String title, long updatedAt) {
            this.id = id; this.title = title; this.updatedAt = updatedAt;
        }
    }

    private static final String PREFS = "ei_chat_history";
    private static final String KEY_DATA = "conversations";
    private static final String KEY_CURRENT = "current_id";
    private static final int MAX_CONVERSATIONS = 30;
    private static final int MAX_MESSAGES = 80;

    private final SharedPreferences prefs;
    private final ArrayList<Conversation> conversations = new ArrayList<>();
    private String currentId;

    public ChatHistoryStore(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (conversations.isEmpty()) newConversation();
        if (find(currentId) == null) currentId = conversations.get(0).id;
        save();
    }

    public synchronized Conversation current() {
        Conversation c = find(currentId);
        if (c == null) c = newConversation();
        return c;
    }

    public synchronized Conversation newConversation() {
        Conversation c = new Conversation(UUID.randomUUID().toString(), "Novo chat", System.currentTimeMillis());
        conversations.add(c);
        currentId = c.id;
        trim();
        save();
        return c;
    }

    public synchronized void select(String id) {
        if (find(id) != null) { currentId = id; save(); }
    }

    public synchronized List<Conversation> recent() {
        ArrayList<Conversation> copy = new ArrayList<>(conversations);
        Collections.sort(copy, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return copy;
    }

    public synchronized void append(String role, String text) {
        if (text == null || text.trim().isEmpty()) return;
        Conversation c = current();
        c.messages.add(new Message(role, text.trim()));
        while (c.messages.size() > MAX_MESSAGES) c.messages.remove(0);
        if ("user".equals(role) && ("Novo chat".equals(c.title) || c.title.trim().isEmpty())) {
            c.title = makeTitle(text);
        }
        c.updatedAt = System.currentTimeMillis();
        trim();
        save();
    }

    public synchronized JSONArray apiInput(int maxMessages) {
        JSONArray input = new JSONArray();
        Conversation c = current();
        int start = Math.max(0, c.messages.size() - Math.max(2, maxMessages));
        for (int i = start; i < c.messages.size(); i++) {
            Message m = c.messages.get(i);
            if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.text);
                input.put(o);
            } catch (Exception ignored) {}
        }
        return input;
    }

    private Conversation find(String id) {
        if (id == null) return null;
        for (Conversation c : conversations) if (id.equals(c.id)) return c;
        return null;
    }

    private static String makeTitle(String text) {
        String t = text == null ? "Novo chat" : text.trim().replaceAll("\\s+", " ");
        if (t.length() > 38) t = t.substring(0, 38).trim() + "…";
        return t.isEmpty() ? "Novo chat" : t;
    }

    private void trim() {
        if (conversations.size() <= MAX_CONVERSATIONS) return;
        conversations.sort(Comparator.comparingLong(a -> a.updatedAt));
        while (conversations.size() > MAX_CONVERSATIONS) {
            Conversation victim = conversations.get(0);
            if (victim.id.equals(currentId) && conversations.size() > 1) victim = conversations.get(1);
            conversations.remove(victim);
        }
    }

    private void load() {
        conversations.clear();
        currentId = prefs.getString(KEY_CURRENT, null);
        String raw = prefs.getString(KEY_DATA, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                Conversation c = new Conversation(o.optString("id", UUID.randomUUID().toString()), o.optString("title", "Novo chat"), o.optLong("updated_at", 0));
                JSONArray ms = o.optJSONArray("messages");
                if (ms != null) for (int j = 0; j < ms.length(); j++) {
                    JSONObject m = ms.optJSONObject(j); if (m == null) continue;
                    String role = m.optString("role", ""); String text = m.optString("text", "");
                    if ((!"user".equals(role) && !"assistant".equals(role)) || text.trim().isEmpty()) continue;
                    c.messages.add(new Message(role, text));
                }
                conversations.add(c);
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Conversation c : conversations) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("title", c.title); o.put("updated_at", c.updatedAt);
                JSONArray ms = new JSONArray();
                for (Message m : c.messages) {
                    JSONObject x = new JSONObject(); x.put("role", m.role); x.put("text", m.text); ms.put(x);
                }
                o.put("messages", ms); a.put(o);
            }
            prefs.edit().putString(KEY_DATA, a.toString()).putString(KEY_CURRENT, currentId).apply();
        } catch (Exception ignored) {}
    }
}
