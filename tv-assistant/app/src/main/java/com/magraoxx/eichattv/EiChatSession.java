package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Dialog;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class EiChatSession extends android.service.voice.VoiceInteractionSession {
    private TextView title, status, pulse, userLine, replyLine, micState;
    private SpeechRecognizer speech;
    private TextToSpeech tts;
    private boolean ttsReady, continuous;
    private String pendingSpeech;
    private Runnable pendingAfter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CommandEngine engine;
    private OpenAiClient ai;
    private ChatGptAuth auth;
    private MusicRecognitionClient music;
    private AiVoiceRecorder audioRecorder;

    private Bitmap screenBitmap;
    private String screenContext = "";
    private boolean screenshotResolved;
    private boolean assistResolved;
    private String pendingVisionQuery;
    private Runnable pendingVisionTimeout;

    public EiChatSession(Context c) {
        super(c);
        engine = new CommandEngine(c);
        ai = new OpenAiClient(c);
        auth = new ChatGptAuth(c);
        music = new MusicRecognitionClient(c);
        audioRecorder = new AiVoiceRecorder(c);
    }

    private int dp(int v) { return Math.round(v * getContext().getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    private TextView tv(String s, float size) {
        TextView v = new TextView(getContext());
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    @Override public View onCreateContentView() {
        Dialog d = getWindow();
        Window w = d == null ? null : d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER_HORIZONTAL);
        shell.setPadding(dp(26), 0, dp(26), dp(30));

        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(bg(Color.argb(238, 24, 27, 33), 28, Color.argb(210, 84, 91, 105)));
        int screenPx = getContext().getResources().getDisplayMetrics().widthPixels;
        int overlayWidth = Math.min(dp(980), Math.max(dp(720), (int) (screenPx * 0.62f)));
        shell.addView(box, new LinearLayout.LayoutParams(overlayWidth, -2));

        LinearLayout voiceCol = new LinearLayout(getContext());
        voiceCol.setGravity(Gravity.CENTER);
        pulse = tv("", 1);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.argb(120, 15, 47, 88));
        ring.setStroke(dp(4), Color.rgb(55, 145, 255));
        pulse.setBackground(ring);
        pulse.setElevation(dp(8));
        voiceCol.addView(pulse, new LinearLayout.LayoutParams(dp(64), dp(64)));
        box.addView(voiceCol, new LinearLayout.LayoutParams(dp(82), -2));

        LinearLayout center = new LinearLayout(getContext());
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(8), 0, dp(12), 0);
        box.addView(center, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        title = tv("Ei Chat", 19);
        title.setTypeface(null, 1);
        heading.addView(title, new LinearLayout.LayoutParams(-2, dp(30)));
        status = tv("Pronto", 14);
        status.setTextColor(Color.rgb(178, 188, 204));
        status.setPadding(dp(14), 0, 0, 0);
        status.setMaxLines(1);
        heading.addView(status, new LinearLayout.LayoutParams(0, dp(30), 1f));
        center.addView(heading, new LinearLayout.LayoutParams(-1, dp(32)));

        userLine = tv("Fale comigo para começar", 14);
        userLine.setTextColor(Color.rgb(236, 239, 244));
        userLine.setPadding(dp(14), dp(7), dp(14), dp(7));
        userLine.setMaxLines(2);
        userLine.setBackground(bg(Color.argb(230, 57, 62, 72), 16, Color.argb(130, 104, 112, 126)));
        LinearLayout.LayoutParams userLp = new LinearLayout.LayoutParams(-1, -2);
        userLp.setMargins(0, dp(6), 0, dp(6));
        center.addView(userLine, userLp);

        replyLine = tv("✦ Ei Chat pronto para ajudar", 14);
        replyLine.setTextColor(Color.WHITE);
        replyLine.setPadding(dp(14), dp(8), dp(14), dp(8));
        replyLine.setMaxLines(3);
        replyLine.setBackground(bg(Color.argb(235, 39, 55, 88), 16, Color.argb(150, 67, 116, 205)));
        center.addView(replyLine, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout micCol = new LinearLayout(getContext());
        micCol.setOrientation(LinearLayout.VERTICAL);
        micCol.setGravity(Gravity.CENTER);
        TextView micIcon = tv("🎙", 24);
        micIcon.setGravity(Gravity.CENTER);
        micIcon.setTextColor(Color.rgb(95, 168, 255));
        micIcon.setBackground(bg(Color.argb(155, 22, 55, 96), 24, Color.argb(170, 55, 145, 255)));
        micCol.addView(micIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        micState = tv("Ouvindo…", 12);
        micState.setGravity(Gravity.CENTER);
        micState.setTextColor(Color.rgb(210, 218, 230));
        LinearLayout.LayoutParams micTextLp = new LinearLayout.LayoutParams(dp(92), dp(28));
        micTextLp.setMargins(0, dp(4), 0, 0);
        micCol.addView(micState, micTextLp);
        box.addView(micCol, new LinearLayout.LayoutParams(dp(102), -2));

        return shell;
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        screenshotResolved = false;
        assistResolved = false;
        screenBitmap = null;
        screenContext = "";
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("session_active", true)
                .putLong("session_opened_at", System.currentTimeMillis())
                .putInt("session_show_flags", flags)
                .putInt("disabled_show_context", getDisabledShowContext())
                .putInt("user_disabled_show_context", getUserDisabledShowContext())
                .apply();
        if (title == null) setContentView(onCreateContentView());
        continuous = args != null && args.getBoolean("continuous", false);
        String initial = args == null ? "" : args.getString("initial_query", "").trim();
        if (!initial.isEmpty()) {
            set("Ei Chat • ativado", "Comando recebido: “" + initial + "”", Color.rgb(120, 185, 255));
            main.postDelayed(() -> handle(initial), 160);
        } else {
            startListening();
        }
    }

    @Override
    public void onHandleScreenshot(Bitmap screenshot) {
        screenshotResolved = true;
        screenBitmap = screenshot;
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_screen_capture_ok", screenshot != null)
                .putLong("last_screen_capture_at", System.currentTimeMillis())
                .putInt("last_screen_width", screenshot == null ? 0 : screenshot.getWidth())
                .putInt("last_screen_height", screenshot == null ? 0 : screenshot.getHeight())
                .apply();
        flushPendingVisionIfReady();
    }

    @Override
    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
        assistResolved = true;
        screenContext = buildScreenContext(structure, content);
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_assist_context_ok", !screenContext.isEmpty())
                .putLong("last_assist_context_at", System.currentTimeMillis())
                .putString("last_assist_context_preview", screenContext.length() > 500 ? screenContext.substring(0, 500) : screenContext)
                .apply();
        flushPendingVisionIfReady();
    }

    private void set(String t, String s, int color) {
        if (pulse != null) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.argb(120, 15, 47, 88));
            ring.setStroke(dp(4), color);
            pulse.setBackground(ring);
        }
        if ("Você".equals(t)) {
            if (title != null) title.setText("Ei Chat");
            if (status != null) status.setText("Pedido recebido");
            if (userLine != null) userLine.setText("●  " + s);
            if (micState != null) micState.setText("Processando…");
            return;
        }
        if (title != null) title.setText("Ei Chat");
        String state = "Pronto";
        if (t != null) {
            if (t.contains("ouvindo")) state = "Ouvindo…";
            else if (t.contains("entendendo")) state = "Entendendo…";
            else if (t.contains("pensando")) state = "Analisando…";
            else if (t.contains("olhando")) state = "Analisando a tela…";
            else if (t.contains("visão")) state = "Visão contextual";
            else if (t.contains("música encontrada")) state = "Música identificada";
            else if (t.contains("música")) state = "Reconhecimento musical";
            else if (t.contains("ativado")) state = "Ativado";
            else if (t.contains("chamada")) state = "Conversa contínua";
        }
        if (status != null) status.setText(state);
        if (replyLine != null && s != null && !s.trim().isEmpty()) replyLine.setText("✦  " + s);
        if (micState != null) {
            if (state.startsWith("Ouvindo")) micState.setText("Ouvindo…");
            else if (state.startsWith("Entendendo")) micState.setText("Entendendo…");
            else if (state.contains("Analisando") || state.contains("Visão") || state.contains("Reconhecimento")) micState.setText("Analisando…");
            else micState.setText("Ativo");
        }
    }

    private void startListening() {
        if (!auth.isConnected()) {
            set("Ei Chat", "Abra o aplicativo e conecte sua conta ChatGPT primeiro.", Color.rgb(255, 180, 80));
            speak("Abra o Ei Chat e conecte sua conta ChatGPT primeiro.", this::hide);
            return;
        }
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            set("Ei Chat", "Permissão de microfone necessária.", Color.rgb(255, 180, 80));
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            set("Ei Chat", "Reconhecimento de voz indisponível nesta TV.", Color.rgb(255, 105, 105));
            return;
        }
        destroySpeech();
        speech = VoiceRecognizerHelper.create(getContext());
        speech.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { set("Ei Chat • ouvindo", "Pode falar…", Color.rgb(120, 185, 255)); }
            public void onBeginningOfSpeech() { set("Ei Chat • ouvindo", "Estou ouvindo você…", Color.rgb(120, 185, 255)); }
            public void onRmsChanged(float r) { if (pulse != null) { float z = r > 2f ? 1.12f : 1f; pulse.animate().scaleX(z).scaleY(z).setDuration(120).start(); } }
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { set("Ei Chat • entendendo", "Entendendo sua voz…", Color.rgb(190, 150, 255)); }
            public void onError(int e) {
                set("Ei Chat", "Não consegui ouvir. Tente novamente.", Color.rgb(255, 105, 105));
                if (continuous) main.postDelayed(this::hide, 2200);
            }
            public void onResults(Bundle b) {
                ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (a == null || a.isEmpty() || a.get(0).trim().isEmpty()) {
                    set("Ei Chat", "Não entendi.", Color.rgb(255, 180, 80));
                    if (continuous) main.postDelayed(this::hide, 2200);
                    return;
                }
                handle(a.get(0).trim());
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
        android.content.Intent i = VoiceRecognizerHelper.intent();
        try {
            speech.startListening(i);
        } catch (Throwable e) {
            set("Ei Chat", "Falha ao abrir o microfone.", Color.rgb(255, 105, 105));
        }
    }

    private void handle(String q) {
        String n = norm(q);
        if (n.contains("encerrar chamada") || n.contains("finalizar chamada") || n.equals("encerrar")) {
            continuous = false;
            destroySpeech();
            set("Ei Chat", "Chamada encerrada.", Color.rgb(130, 220, 165));
            speak("Chamada encerrada.", this::hide);
            return;
        }
        if ((n.contains("iniciar chamada") || n.contains("inicie chamada")) && n.contains("voz")) {
            continuous = true;
            set("Ei Chat • chamada", "Conversa contínua ativada.", Color.rgb(130, 220, 165));
            speak("Chamada de voz iniciada. Pode falar comigo sem repetir o comando.", this::startListening);
            return;
        }

        set("Você", q, Color.rgb(120, 185, 255));
        if (isMusicRecognitionRequest(n)) {
            recognizeMusic();
            return;
        }
        if (isVisionRequest(n)) {
            analyzeCurrentScreen(q);
            return;
        }

        CommandEngine.Result r = engine.execute(q);
        if (r.handled) {
            set("Ei Chat", r.message, Color.rgb(130, 220, 165));
            speak(r.message, afterReply());
            return;
        }
        set("Ei Chat • pensando", "Analisando…", Color.rgb(190, 150, 255));
        ai.ask(q, new OpenAiClient.Callback() {
            public void onSuccess(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat", x, Color.rgb(130, 220, 165));
                    speak(x, afterReply());
                });
            }
            public void onError(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat", x, Color.rgb(255, 105, 105));
                    if (continuous) main.postDelayed(this::hide, 2200);
                });
            }
        });
    }

    private void recognizeMusic() {
        destroySpeech();
        if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
        set("Ei Chat • música", "Vou ouvir alguns segundos da música…", Color.rgb(120, 185, 255));
        audioRecorder.recordMusic(new AiVoiceRecorder.Callback() {
            @Override public void onRecorded(java.io.File wav, String route) {
                getContext().getMainExecutor().execute(() -> set("Ei Chat • música", "Áudio capturado. Identificando a faixa…", Color.rgb(190, 150, 255)));
                music.recognize(wav, new MusicRecognitionClient.Callback() {
                    @Override public void onMatch(String title, String artist, String album, String link) {
                        getContext().getMainExecutor().execute(() -> {
                            StringBuilder b = new StringBuilder();
                            if (!title.isEmpty()) b.append(title);
                            if (!artist.isEmpty()) b.append(b.length() == 0 ? artist : " — " + artist);
                            if (!album.isEmpty()) b.append(". Álbum: ").append(album);
                            String answer = b.length() == 0 ? "Encontrei a música, mas o serviço não retornou título e artista." : "É " + b.toString() + ".";
                            set("Ei Chat • música encontrada", answer, Color.rgb(130, 220, 165));
                            speak(answer, afterReply());
                        });
                    }
                    @Override public void onNoMatch() {
                        getContext().getMainExecutor().execute(() -> {
                            String answer = "Não consegui reconhecer essa música nesse trecho. Deixe a música tocar mais limpa e tente novamente.";
                            set("Ei Chat • música", answer, Color.rgb(255, 180, 80));
                            speak(answer, afterReply());
                        });
                    }
                    @Override public void onError(String message) {
                        getContext().getMainExecutor().execute(() -> {
                            set("Ei Chat • música", message, Color.rgb(255, 105, 105));
                            speak(message, afterReply());
                        });
                    }
                });
            }
            @Override public void onError(String error) {
                getContext().getMainExecutor().execute(() -> {
                    String message = "Não consegui ouvir a música: " + error;
                    set("Ei Chat • música", message, Color.rgb(255, 105, 105));
                    speak(message, afterReply());
                });
            }
        });
    }

    private static boolean isMusicRecognitionRequest(String n) {
        if (n == null) return false;
        return n.contains("que musica e essa") || n.contains("qual musica e essa") ||
                n.contains("que musica esta tocando") || n.contains("qual musica esta tocando") ||
                n.contains("que som e esse") || n.contains("qual som e esse") ||
                n.contains("descobre essa musica") || n.contains("descobrir essa musica") ||
                n.contains("reconhece essa musica") || n.contains("reconhecer essa musica");
    }

    private void analyzeCurrentScreen(String query) {
        destroySpeech();
        set("Ei Chat • olhando a tela", "Capturando e analisando o que está aparecendo…", Color.rgb(190, 150, 255));
        if (screenBitmap != null || screenshotResolved) {
            runVisionQuery(query);
            return;
        }
        pendingVisionQuery = query;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = () -> {
            if (pendingVisionQuery == null) return;
            String q = pendingVisionQuery;
            pendingVisionQuery = null;
            runVisionQuery(q);
        };
        main.postDelayed(pendingVisionTimeout, 900);
    }

    private void flushPendingVisionIfReady() {
        if (pendingVisionQuery == null) return;
        if (screenBitmap == null && !screenshotResolved) return;
        String q = pendingVisionQuery;
        pendingVisionQuery = null;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = null;
        runVisionQuery(q);
    }

    private void runVisionQuery(String query) {
        Bitmap image = screenBitmap;
        String context = screenContext;
        if (image == null && (context == null || context.trim().isEmpty())) {
            String msg = "Não consegui receber a imagem nem o contexto desta tela. Esse aplicativo, entrada HDMI ou conteúdo protegido pode estar bloqueando a captura.";
            set("Ei Chat • visão indisponível", msg, Color.rgb(255, 180, 80));
            speak(msg, afterReply());
            return;
        }

        String indicator = image != null
                ? "Imagem da tela recebida. Analisando detalhes…"
                : "A imagem foi bloqueada; analisando o contexto textual disponível…";
        set("Ei Chat • visão", indicator, Color.rgb(190, 150, 255));

        ai.askWithScreen(query, image, context, new OpenAiClient.Callback() {
            @Override public void onSuccess(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat • visão", x, Color.rgb(130, 220, 165));
                    speak(x, afterReply());
                });
            }

            @Override public void onError(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat • visão", x, Color.rgb(255, 105, 105));
                    if (continuous) main.postDelayed(this::hide, 2200);
                });
            }
        });
    }

    private static boolean isVisionRequest(String n) {
        if (n == null || n.isEmpty()) return false;
        String[] phrases = new String[]{
                "analisa essa", "analise essa", "analisa isso", "analise isso",
                "olha essa", "olhe essa", "olha isso", "olhe isso",
                "essa parte", "essa tela", "nessa tela", "o que tem na tela", "o que esta na tela",
                "que jogo e esse", "qual jogo e esse", "que jogo estou", "qual jogo estou",
                "que filme e esse", "qual filme e esse", "que filme e", "qual filme e",
                "que serie e essa", "qual serie e essa", "que serie e", "qual serie e",
                "que musica e essa", "qual musica e essa", "que musica e", "qual musica e",
                "onde eu estou", "onde estou", "em que parte", "parte do mapa", "no mapa",
                "pra onde eu vou", "para onde eu vou", "pra onde ir", "para onde ir",
                "como passo daqui", "como passar daqui", "como passo essa", "como passar essa",
                "essa fase", "esse puzzle", "esse quebra cabeca", "essa senha", "qual a senha",
                "que item e esse", "qual item e esse", "esse item", "esse chefe", "esse boss",
                "o que faco aqui", "o que fazer aqui", "me ajuda aqui", "me ajude aqui"
        };
        for (String p : phrases) if (n.contains(p)) return true;
        return false;
    }

    private static String buildScreenContext(AssistStructure structure, AssistContent content) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        try {
            if (structure != null) {
                ComponentName component = structure.getActivityComponent();
                if (component != null) {
                    out.append("Aplicativo/atividade: ").append(component.flattenToShortString()).append('\n');
                }
                int windows = structure.getWindowNodeCount();
                for (int i = 0; i < windows && seen.size() < 100; i++) {
                    AssistStructure.WindowNode win = structure.getWindowNodeAt(i);
                    if (win != null) collectNodeText(win.getRootViewNode(), seen, 100);
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (content != null) {
                if (content.getWebUri() != null) out.append("URL/contexto web: ").append(content.getWebUri()).append('\n');
                if (content.getClipData() != null) out.append("Há conteúdo compartilhado pela atividade.\n");
            }
        } catch (Throwable ignored) {}

        if (!seen.isEmpty()) {
            out.append("Textos/descrições visíveis:\n");
            int count = 0;
            for (String s : seen) {
                out.append("- ").append(s).append('\n');
                if (++count >= 100 || out.length() > 6500) break;
            }
        }
        return out.toString().trim();
    }

    private static void collectNodeText(AssistStructure.ViewNode node, Set<String> seen, int max) {
        if (node == null || seen.size() >= max) return;
        try {
            CharSequence text = node.getText();
            addVisibleText(seen, text, max);
            CharSequence desc = node.getContentDescription();
            addVisibleText(seen, desc, max);
            String id = node.getIdEntry();
            if (id != null && !id.trim().isEmpty() && id.length() < 80) addVisibleText(seen, id, max);
        } catch (Throwable ignored) {}
        int children = 0;
        try { children = node.getChildCount(); } catch (Throwable ignored) {}
        for (int i = 0; i < children && seen.size() < max; i++) {
            try { collectNodeText(node.getChildAt(i), seen, max); } catch (Throwable ignored) {}
        }
    }

    private static void addVisibleText(Set<String> seen, CharSequence cs, int max) {
        if (cs == null || seen.size() >= max) return;
        String s = cs.toString().replaceAll("\\s+", " ").trim();
        if (s.length() < 2 || s.length() > 300) return;
        seen.add(s);
    }

    private Runnable afterReply() {
        return () -> {
            if (continuous) main.postDelayed(() -> startListening(), 300);
            else main.postDelayed(this::hide, 2600);
        };
    }

    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(getContext(), x -> {
            if (x == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.02f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        Runnable r = pendingAfter;
                        pendingAfter = null;
                        if (r != null) main.post(r);
                    }
                    @Override public void onError(String id) {
                        Runnable r = pendingAfter;
                        pendingAfter = null;
                        if (r != null) main.post(r);
                    }
                });
                String p = pendingSpeech;
                pendingSpeech = null;
                if (p != null) speak(p, pendingAfter);
            }
        });
    }

    private void speak(String s, Runnable after) {
        if (s == null || s.trim().isEmpty()) {
            if (after != null) after.run();
            return;
        }
        pendingAfter = after;
        if (!ttsReady) {
            pendingSpeech = s;
            initTts();
            return;
        }
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ei_chat_session");
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private void destroySpeech() {
        if (speech != null) {
            try { speech.cancel(); } catch (Throwable ignored) {}
            try { speech.destroy(); } catch (Throwable ignored) {}
            speech = null;
        }
    }

    private void clearVisionState() {
        pendingVisionQuery = null;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = null;
        screenBitmap = null;
        screenContext = "";
        screenshotResolved = false;
        assistResolved = false;
    }

    private void markSessionClosed() {
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("session_active", false)
                .putLong("session_closed_at", System.currentTimeMillis())
                .apply();
    }

    @Override public void onHide() {
        markSessionClosed();
        destroySpeech();
        clearVisionState();
        if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
        super.onHide();
    }

    @Override public void onDestroy() {
        markSessionClosed();
        destroySpeech();
        clearVisionState();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
