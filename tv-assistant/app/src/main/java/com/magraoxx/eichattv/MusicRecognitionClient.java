package com.magraoxx.eichattv;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MusicRecognitionClient {
    public interface Callback {
        void onMatch(String title, String artist, String album, String link);
        void onNoMatch();
        void onError(String message);
    }

    private final Context context;

    public MusicRecognitionClient(Context c) {
        context = c.getApplicationContext();
    }

    public boolean isConfigured() {
        return !token().isEmpty();
    }

    public void recognize(File wav, Callback cb) {
        new Thread(() -> {
            try {
                String token = token();
                String boundary = "----EiChat" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.audd.io/").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(45000);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    if (!token.isEmpty()) field(out, boundary, "api_token", token);
                    field(out, boundary, "return", "spotify,apple_music");
                    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"ei-chat.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
                    out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = new FileInputStream(wav)) {
                        byte[] b = new byte[8192]; int n;
                        while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    }
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int status = c.getResponseCode();
                String body = readAll(status >= 400 ? c.getErrorStream() : c.getInputStream());
                if (status < 200 || status >= 300) throw new Exception("Serviço musical " + status);
                JSONObject j = new JSONObject(body);
                if (!"success".equalsIgnoreCase(j.optString("status"))) {
                    JSONObject e = j.optJSONObject("error");
                    throw new Exception(e == null ? "Falha no reconhecimento musical" : e.optString("error_message", e.optString("message", "Falha no reconhecimento musical")));
                }
                JSONObject r = j.optJSONObject("result");
                if (r == null) { cb.onNoMatch(); return; }
                cb.onMatch(r.optString("title", ""), r.optString("artist", ""), r.optString("album", ""), r.optString("song_link", ""));
            } catch (Throwable e) {
                cb.onError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }, "ei-chat-music-recognition").start();
    }

    public void saveToken(String token) {
        context.getSharedPreferences("music_recognition", Context.MODE_PRIVATE)
                .edit().putString("audd_token", token == null ? "" : token.trim()).apply();
    }

    private String token() {
        return context.getSharedPreferences("music_recognition", Context.MODE_PRIVATE)
                .getString("audd_token", "").trim();
    }

    private static void field(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String s;
        while ((s = r.readLine()) != null) b.append(s);
        return b.toString();
    }
}
