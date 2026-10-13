package com.magraoxx.eichattv;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class OpenAiClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private final ChatGptAuth auth;
    private volatile String cachedModel;

    public OpenAiClient(Context c) {
        auth = new ChatGptAuth(c);
    }

    // Sign in with ChatGPT currently supports text/image/file Responses input, not /audio/transcriptions.
    public void transcribe(java.io.File wav, Callback cb) {
        cb.onError("O login ChatGPT não autoriza transcrição de áudio. Use o reconhecimento de voz do aparelho para converter a fala em texto.");
    }

    public void ask(String prompt, Callback cb) {
        run(prompt, null, null, false, cb);
    }

    public void askConversation(JSONArray messages, Callback cb) {
        new Thread(() -> {
            try {
                String token = auth.accessToken();
                String model = pickModel(token);
                JSONObject req = new JSONObject();
                req.put("model", model);
                req.put("store", false);
                req.put("stream", true);
                req.put("instructions",
                        "Você é Ei Chat, assistente pessoal em uma Android/Google TV. " +
                        "Converse em português do Brasil de forma natural, clara e útil. " +
                        "Mantenha o contexto das mensagens anteriores, faça respostas adequadas para leitura na TV e para voz, " +
                        "e não repita informações que já estejam claras na conversa.");
                JSONArray safeMessages = messages == null ? new JSONArray() : new JSONArray(messages.toString());
                if (safeMessages.length() == 0) throw new Exception("A conversa está vazia.");
                req.put("input", safeMessages);
                cb.onSuccess(sendStreaming(token, req));
            } catch (Throwable e) {
                cb.onError("IA: " + safe(e));
            }
        }, "ei-chat-conversation").start();
    }

    public void askWithScreen(String prompt, Bitmap screenshot, String screenContext, Callback cb) {
        run(prompt, screenshot, screenContext, true, cb);
    }

    private void run(String prompt, Bitmap screenshot, String screenContext, boolean visualMode, Callback cb) {
        new Thread(() -> {
            try {
                String token = auth.accessToken();
                String model = pickModel(token);

                JSONObject req = new JSONObject();
                req.put("model", model);
                req.put("store", false);
                req.put("stream", true);

                if (visualMode) {
                    req.put("instructions",
                            "Você é Ei Chat, assistente visual de uma Android/Google TV. " +
                            "Responda em português do Brasil, de forma curta, natural e útil para ser falada na TV. " +
                            "Analise cuidadosamente a captura e o contexto da tela. Para jogos, use HUD, mapa, missão, cenário, " +
                            "texto e posição visível para orientar o usuário. Quando for útil, use pesquisa na web para confirmar " +
                            "walkthroughs, mapas, objetivos, nomes de fases, chefes ou informações atuais. Para filmes/séries, só identifique " +
                            "quando houver pistas suficientes. Para música, use apenas informações visuais disponíveis; se a música não puder " +
                            "ser identificada sem ouvir o áudio, diga isso claramente. Nunca invente o nome de um jogo, filme, música, personagem, " +
                            "local ou objetivo quando a evidência visual for insuficiente. Se a imagem estiver bloqueada, preta ou sem informação " +
                            "suficiente, diga o que o usuário deve mostrar na tela para uma nova análise.");

                    JSONArray tools = new JSONArray();
                    JSONObject web = new JSONObject();
                    web.put("type", "web_search");
                    tools.put(web);
                    req.put("tools", tools);
                    req.put("tool_choice", "auto");
                } else {
                    req.put("instructions",
                            "Você é Ei Chat, assistente de uma Android/Google TV. Responda em português do Brasil, de forma curta, natural e útil para ser falada em uma televisão.");
                }

                JSONArray input = new JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");

                if (visualMode) {
                    JSONArray content = new JSONArray();
                    StringBuilder text = new StringBuilder();
                    text.append(prompt == null ? "" : prompt.trim());
                    if (screenContext != null && !screenContext.trim().isEmpty()) {
                        String ctx = screenContext.trim();
                        if (ctx.length() > 7000) ctx = ctx.substring(0, 7000);
                        text.append("\n\nContexto fornecido pela TV:\n").append(ctx);
                    }
                    JSONObject textPart = new JSONObject();
                    textPart.put("type", "input_text");
                    textPart.put("text", text.toString());
                    content.put(textPart);

                    if (screenshot != null) {
                        JSONObject imagePart = new JSONObject();
                        imagePart.put("type", "input_image");
                        imagePart.put("detail", "high");
                        imagePart.put("image_url", bitmapDataUrl(screenshot));
                        content.put(imagePart);
                    }
                    msg.put("content", content);
                } else {
                    msg.put("content", prompt == null ? "" : prompt);
                }

                input.put(msg);
                req.put("input", input);

                String answer;
                try {
                    answer = sendStreaming(token, req);
                } catch (Exception first) {
                    if (visualMode && req.has("tools") && isWebToolCompatibilityError(first)) {
                        req.remove("tools");
                        req.remove("tool_choice");
                        answer = sendStreaming(token, req);
                    } else {
                        throw first;
                    }
                }
                cb.onSuccess(answer);
            } catch (Throwable e) {
                cb.onError("IA: " + safe(e));
            }
        }, visualMode ? "ei-chat-vision" : "ei-chat-ai").start();
    }

    private static boolean isWebToolCompatibilityError(Throwable e) {
        String m = safe(e).toLowerCase(Locale.ROOT);
        return m.contains("web_search") || m.contains("web search") ||
                (m.contains("tool") && (m.contains("support") || m.contains("invalid") || m.contains("unknown") || m.contains("not allowed")));
    }

    private String sendStreaming(String token, JSONObject req) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream");
        try (OutputStream o = c.getOutputStream()) {
            o.write(req.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = c.getResponseCode();
        if (status < 200 || status >= 300) {
            String body = readAll(c.getErrorStream());
            throw new Exception(apiError(status, body));
        }

        StringBuilder out = new StringBuilder();
        boolean completed = false;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JSONObject ev = new JSONObject(data);
            String type = ev.optString("type", "");
            if ("response.output_text.delta".equals(type)) {
                out.append(ev.optString("delta", ""));
            } else if ("response.completed".equals(type)) {
                completed = true;
            } else if ("response.failed".equals(type)) {
                JSONObject response = ev.optJSONObject("response");
                JSONObject error = response == null ? null : response.optJSONObject("error");
                throw new Exception(error == null
                        ? "A IA não concluiu a resposta."
                        : error.optString("message", error.optString("code", "Falha na IA")));
            } else if ("response.incomplete".equals(type)) {
                throw new Exception("A resposta da IA ficou incompleta.");
            }
        }
        r.close();
        if (!completed) throw new Exception("A conexão com a IA terminou antes de concluir a resposta.");
        String text = out.toString().trim();
        if (text.isEmpty()) throw new Exception("A IA respondeu sem texto.");
        return text;
    }

    private static String bitmapDataUrl(Bitmap original) throws Exception {
        Bitmap work = original;
        int w = original.getWidth();
        int h = original.getHeight();
        int max = Math.max(w, h);
        if (max > 1600) {
            float scale = 1600f / max;
            int nw = Math.max(1, Math.round(w * scale));
            int nh = Math.max(1, Math.round(h * scale));
            work = Bitmap.createScaledBitmap(original, nw, nh, true);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!work.compress(Bitmap.CompressFormat.JPEG, 82, bytes)) {
            if (work != original) work.recycle();
            throw new Exception("Não consegui preparar a imagem da tela.");
        }
        if (work != original) work.recycle();
        String b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + b64;
    }

    private String pickModel(String token) throws Exception {
        if (cachedModel != null && !cachedModel.isEmpty()) return cachedModel;
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/models").openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        int s = c.getResponseCode();
        String body = readAll(s >= 400 ? c.getErrorStream() : c.getInputStream());
        if (s < 200 || s >= 300) throw new Exception(apiError(s, body));
        JSONObject j = new JSONObject(body);
        JSONArray a = j.optJSONArray("models");
        if (a == null) throw new Exception("Sua conta não retornou modelos disponíveis.");
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m == null) continue;
            String slug = m.optString("slug", m.optString("id", ""));
            if (!slug.isEmpty() && "list".equals(m.optString("visibility", "list"))) {
                cachedModel = slug;
                return slug;
            }
        }
        throw new Exception("Nenhum modelo do ChatGPT está disponível para esta conta.");
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        String x;
        while ((x = r.readLine()) != null) b.append(x);
        r.close();
        return b.toString();
    }

    private static String apiError(int s, String body) {
        try {
            JSONObject j = new JSONObject(body);
            JSONObject e = j.optJSONObject("error");
            if (e != null) return "OpenAI " + s + ": " + e.optString("message", e.optString("code", body));
            String d = j.optString("detail", "");
            return "OpenAI " + s + (d.isEmpty() ? "" : ": " + d);
        } catch (Exception e) {
            return "OpenAI " + s + (body == null || body.isEmpty() ? "" : ": " + body);
        }
    }

    private static String safe(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
