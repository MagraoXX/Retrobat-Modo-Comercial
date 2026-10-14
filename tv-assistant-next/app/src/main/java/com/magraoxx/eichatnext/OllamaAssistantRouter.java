package com.magraoxx.eichatnext;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class OllamaAssistantRouter implements AssistantRouter {
    private static final String SYSTEM_PROMPT =
            "Você é o Ei Chat, assistente de uma Android/Google TV. Responda em português brasileiro, " +
            "de forma natural e útil. Se houver imagem da tela, use apenas o que realmente estiver visível. " +
            "Em jogos, ajude com puzzle, mapa, objetivo, menu, item e estratégia visível. " +
            "Em filmes, séries e vídeos, explique cena, legenda, menu e contexto sem inventar detalhes. " +
            "Nunca afirme que viu HDMI ou console se não recebeu imagem. Seja conciso para conversa por voz.";

    private final String baseUrl;
    private final String model;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> active;
    private volatile HttpURLConnection activeConnection;
    private JSONArray history = new JSONArray();

    public OllamaAssistantRouter(String baseUrl, String model) {
        String clean = baseUrl == null ? "" : baseUrl.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        this.baseUrl = clean;
        this.model = model == null || model.trim().isEmpty() ? "qwen3-vl:4b" : model.trim();
    }

    public synchronized void setHistory(JSONArray messages) {
        try { history = new JSONArray(messages.toString()); }
        catch (Exception ignored) { history = new JSONArray(); }
    }

    @Override public synchronized void ask(String text, ScreenContextProvider.Context screenContext, Callback callback) {
        cancel();
        final JSONArray prior = history;
        active = executor.submit(() -> request(text, screenContext, prior, callback));
    }

    private void request(String text, ScreenContextProvider.Context screenContext, JSONArray prior, Callback callback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/api/chat");
            if (Thread.currentThread().isInterrupted()) return;
            connection = (HttpURLConnection) url.openConnection();
            activeConnection = connection;
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
            // The screen has already saved this turn; attach it only once below.
            int end = prior.length();
            JSONObject last = end == 0 ? null : prior.optJSONObject(end - 1);
            if (last != null && "user".equals(last.optString("role"))
                    && (text == null ? "" : text).equals(last.optString("content"))) end--;
            for (int i = Math.max(0, end - 20); i < end; i++) {
                JSONObject entry = prior.optJSONObject(i);
                if (entry != null) messages.put(entry);
            }

            JSONObject user = new JSONObject().put("role", "user").put("content", text == null ? "" : text);
            boolean hasVisual = screenContext != null && screenContext.screenshotJpeg != null && screenContext.screenshotJpeg.length > 0;
            if (hasVisual) {
                JSONArray images = new JSONArray();
                images.put(Base64.encodeToString(screenContext.screenshotJpeg, Base64.NO_WRAP));
                user.put("images", images);
            }
            if (screenContext != null && screenContext.structuredText != null && !screenContext.structuredText.trim().isEmpty()) {
                user.put("content", (text == null ? "" : text) + "\n\nContexto textual da tela:\n" + screenContext.structuredText);
            }
            messages.put(user);

            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("think", hasVisual)
                    .put("messages", messages)
                    .put("keep_alive", "10m");

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }

            if (Thread.currentThread().isInterrupted()) return;
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(input);
            if (Thread.currentThread().isInterrupted()) return;
            if (status < 200 || status >= 300) {
                if (callback != null) callback.onError("IA local respondeu HTTP " + status + ": " + trim(response));
                return;
            }

            JSONObject json = new JSONObject(response);
            JSONObject message = json.optJSONObject("message");
            String answer = message == null ? "" : message.optString("content", "").trim();
            if (answer.isEmpty()) {
                if (callback != null) callback.onError("A IA local não retornou texto.");
            } else if (callback != null) {
                callback.onAnswer(answer);
            }
        } catch (Throwable error) {
            if (!Thread.currentThread().isInterrupted() && callback != null) {
                callback.onError("IA local indisponível: " + error.getMessage());
            }
        } finally {
            if (activeConnection == connection) activeConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String trim(String value) {
        if (value == null) return "";
        return value.length() <= 240 ? value : value.substring(0, 240) + "…";
    }

    @Override public synchronized void cancel() {
        Future<?> current = active;
        active = null;
        if (current != null) current.cancel(true);
        HttpURLConnection connection = activeConnection;
        activeConnection = null;
        if (connection != null) connection.disconnect();
    }

    public void release() {
        cancel();
        executor.shutdownNow();
    }
}