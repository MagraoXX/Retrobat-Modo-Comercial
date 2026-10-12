package com.magraoxx.eichattv;

import android.content.Context;
import android.content.SharedPreferences;

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

public final class OpenAiClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OpenAiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void ask(String prompt, Callback callback) {
        executor.execute(() -> {
            try {
                String key = new SecureSecretStore(context).load();
                if (key == null || key.trim().isEmpty()) {
                    callback.onError("A IA ainda não foi configurada. Abra as configurações do Ei Chat e informe sua chave da API.");
                    return;
                }
                SharedPreferences prefs = context.getSharedPreferences("ei_chat_settings", Context.MODE_PRIVATE);
                String model = prefs.getString("ai_model", "gpt-6-astra");

                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("store", false);
                body.put("instructions", "Você é o Ei Chat, assistente de voz de uma TV. Responda em português do Brasil, de forma curta e natural para ser falada em voz alta. Se o usuário pedir uma ação da TV que o app não executou, não finja que executou; explique em uma frase que a ação não foi concluída.");
                body.put("input", prompt);
                body.put("max_output_tokens", 500);

                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + key.trim());

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(stream);
                if (code < 200 || code >= 300) {
                    callback.onError("Falha da IA (HTTP " + code + "). " + extractError(response));
                    return;
                }

                String text = extractOutputText(response);
                if (text == null || text.trim().isEmpty()) {
                    callback.onError("A IA respondeu sem texto utilizável.");
                    return;
                }
                callback.onSuccess(text.trim());
            } catch (Throwable t) {
                callback.onError("Falha ao consultar a IA: " + t.getClass().getSimpleName());
            }
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String extractOutputText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray output = root.optJSONArray("output");
            if (output == null) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;
                    if ("output_text".equals(part.optString("type"))) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(part.optString("text"));
                    }
                }
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractError(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject error = root.optJSONObject("error");
            if (error == null) return "";
            return error.optString("message", "");
        } catch (Throwable ignored) {
            return "";
        }
    }
}
