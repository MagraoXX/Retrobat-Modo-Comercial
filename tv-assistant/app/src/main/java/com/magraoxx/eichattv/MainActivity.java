package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private static final int REQ_ASSISTANT = 1002;

    private FrameLayout rootFrame;
    private LinearLayout chat;
    private LinearLayout historyList;
    private LinearLayout sidebar;
    private LinearLayout callOverlay;
    private ScrollView chatScroll;
    private View sidebarScrim;

    private TextView conversationTitle;
    private TextView accountState;
    private TextView caption;
    private TextView micText;
    private TextView callOrb;
    private TextView callStatus;
    private TextView callTranscript;
    private TextView callMicLabel;

    private EditText input;
    private Button listen;
    private Button account;
    private Button callButton;
    private Button callMute;
    private Button callEnd;
    private Button menuButton;
    private Button sidebarFirst;

    private TextToSpeech tts;
    private CommandEngine engine;
    private OpenAiClient ai;
    private ChatGptAuth auth;
    private ChatHistoryStore history;
    private SpeechRecognizer speech;

    private boolean pendingPermission;
    private boolean pendingVoiceCall;
    private boolean callActive;
    private boolean callMuted;
    private boolean callListening;
    private boolean replyShouldSpeak;
    private Runnable afterSpeech;
    private long lastBackAt;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        engine = new CommandEngine(this);
        ai = new OpenAiClient(this);
        auth = new ChatGptAuth(this);
        history = new ChatHistoryStore(this);
        buildUi();
        refreshAccount();
        renderHistory();
        renderCurrentConversation();

        tts = new TextToSpeech(this, result -> {
            if (result != TextToSpeech.SUCCESS) return;
            tts.setLanguage(new Locale("pt", "BR"));
            tts.setSpeechRate(1.02f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    runOnUiThread(() -> {
                        if (callActive) setCallState("Respondendo", "Ei Chat está falando…", Color.rgb(210, 210, 210));
                    });
                }
                @Override public void onDone(String utteranceId) { finishSpeechCallback(); }
                @Override public void onError(String utteranceId) { finishSpeechCallback(); }
            });
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("start_listening", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startSingleListening, 450);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(238, 238, 238));
        view.setTextSize(size);
        return view;
    }

    private GradientDrawable shape(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private GradientDrawable circle(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (stroke != 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private int lighten(int color) {
        return Color.rgb(
                Math.min(255, Color.red(color) + 18),
                Math.min(255, Color.green(color) + 18),
                Math.min(255, Color.blue(color) + 18));
    }

    private void paintButton(Button button, int base, boolean focused) {
        int fill = focused ? lighten(base) : base;
        int stroke = focused ? Color.rgb(220, 220, 220) : 0;
        button.setBackground(shape(fill, 14, stroke, 2));
        button.setScaleX(focused ? 1.018f : 1f);
        button.setScaleY(focused ? 1.035f : 1f);
        button.setElevation(focused ? dp(6) : 0);
    }

    private Button button(String label, int color, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setMinHeight(dp(44));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setOnClickListener(click);
        paintButton(button, color, false);
        button.setOnFocusChangeListener((v, focused) -> paintButton((Button) v, color, focused));
        return button;
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.rgb(33, 33, 33));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(Color.rgb(33, 33, 33));
        main.setPadding(dp(16), dp(8), dp(16), dp(12));
        rootFrame.addView(main, new FrameLayout.LayoutParams(-1, -1));

        buildTopBar(main);
        buildConversationArea(main);
        buildComposer(main);
        buildSidebar();
        buildCallOverlay();

        caption = text("", 1);
        caption.setVisibility(View.GONE);
        micText = text("", 1);
        micText.setVisibility(View.GONE);

        setContentView(rootFrame);
    }

    private void buildTopBar(LinearLayout main) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        menuButton = button("☰", Color.rgb(33, 33, 33), v -> toggleSidebar());
        menuButton.setTextSize(20);
        top.addView(menuButton, new LinearLayout.LayoutParams(dp(50), dp(48)));

        conversationTitle = text("Novo chat", 16);
        conversationTitle.setTypeface(null, 1);
        conversationTitle.setGravity(Gravity.CENTER_VERTICAL);
        conversationTitle.setPadding(dp(10), 0, 0, 0);
        top.addView(conversationTitle, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button options = button("•••", Color.rgb(33, 33, 33), v -> accountAction());
        options.setTextSize(17);
        top.addView(options, new LinearLayout.LayoutParams(dp(60), dp(48)));

        main.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void buildConversationArea(LinearLayout main) {
        int width = getResources().getDisplayMetrics().widthPixels;
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = Math.max(dp(30), (int) (width * .14f));
        chat.setPadding(sidePadding, dp(8), sidePadding, dp(22));
        chatScroll.addView(chat, new ScrollView.LayoutParams(-1, -2));
        main.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void buildComposer(LinearLayout main) {
        int width = getResources().getDisplayMetrics().widthPixels;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(7), dp(6), dp(7), dp(6));
        composer.setBackground(shape(Color.rgb(47, 47, 47), 27, Color.rgb(76, 76, 76), 1));
        int composerWidth = Math.min(dp(760), (int) (width * .66f));
        wrap.addView(composer, new LinearLayout.LayoutParams(composerWidth, -2));

        Button plus = button("＋", Color.rgb(47, 47, 47), v -> showToolsDialog());
        plus.setTextSize(20);
        composer.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));

        input = new EditText(this);
        input.setHint("Mensagem para o Ei Chat");
        input.setHintTextColor(Color.rgb(155, 155, 155));
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(8), dp(4), dp(8), dp(4));
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setFocusable(true);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));

        listen = button("🎤", Color.rgb(47, 47, 47), v -> startSingleListening());
        composer.addView(listen, new LinearLayout.LayoutParams(dp(50), dp(48)));

        callButton = button("◉", Color.rgb(47, 47, 47), v -> startVoiceCall());
        callButton.setTextSize(19);
        composer.addView(callButton, new LinearLayout.LayoutParams(dp(50), dp(48)));

        Button send = button("↑", Color.rgb(238, 238, 238), v -> sendTyped());
        send.setTextColor(Color.BLACK);
        send.setTextSize(19);
        composer.addView(send, new LinearLayout.LayoutParams(dp(50), dp(48)));

        main.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildSidebar() {
        int width = getResources().getDisplayMetrics().widthPixels;

        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(Color.argb(90, 0, 0, 0));
        sidebarScrim.setVisibility(View.GONE);
        sidebarScrim.setOnClickListener(v -> closeSidebar());
        rootFrame.addView(sidebarScrim, new FrameLayout.LayoutParams(-1, -1));

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(14), dp(14), dp(14), dp(14));
        sidebar.setBackgroundColor(Color.rgb(23, 23, 23));
        sidebar.setVisibility(View.GONE);

        int sidebarWidth = Math.min(dp(300), Math.max(dp(220), (int) (width * .24f)));
        FrameLayout.LayoutParams sidebarParams = new FrameLayout.LayoutParams(sidebarWidth, -1, Gravity.LEFT);
        rootFrame.addView(sidebar, sidebarParams);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("Ei Chat", 18);
        brand.setTypeface(null, 1);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button close = button("×", Color.rgb(23, 23, 23), v -> closeSidebar());
        close.setTextSize(20);
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        sidebar.addView(head, new LinearLayout.LayoutParams(-1, dp(46)));

        sidebarFirst = button("＋  Novo chat", Color.rgb(39, 39, 39), v -> newConversation());
        sidebarFirst.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        sidebarFirst.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams newChatParams = new LinearLayout.LayoutParams(-1, dp(46));
        newChatParams.setMargins(0, dp(8), 0, dp(10));
        sidebar.addView(sidebarFirst, newChatParams);

        TextView chatsLabel = text("Chats", 12);
        chatsLabel.setTextColor(Color.rgb(145, 145, 145));
        chatsLabel.setPadding(dp(6), 0, 0, dp(4));
        sidebar.addView(chatsLabel, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView historyScroll = new ScrollView(this);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyScroll.addView(historyList, new ScrollView.LayoutParams(-1, -2));
        sidebar.addView(historyScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button assistant = button("Assistente da TV", Color.rgb(23, 23, 23), v -> requestAssistantRole());
        assistant.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        Button diagnostics = button("Diagnóstico", Color.rgb(23, 23, 23), v -> runDiagnostics());
        diagnostics.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        sidebar.addView(assistant, new LinearLayout.LayoutParams(-1, dp(44)));
        sidebar.addView(diagnostics, new LinearLayout.LayoutParams(-1, dp(44)));

        accountState = text("ChatGPT", 12);
        accountState.setTextColor(Color.rgb(145, 145, 145));
        accountState.setMaxLines(1);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, dp(28));
        stateParams.setMargins(dp(6), dp(8), 0, 0);
        sidebar.addView(accountState, stateParams);

        account = button("Conta", Color.rgb(39, 39, 39), v -> accountAction());
        account.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        sidebar.addView(account, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private void buildCallOverlay() {
        callOverlay = new LinearLayout(this);
        callOverlay.setOrientation(LinearLayout.VERTICAL);
        callOverlay.setGravity(Gravity.CENTER_HORIZONTAL);
        callOverlay.setPadding(dp(40), dp(22), dp(40), dp(28));
        callOverlay.setBackgroundColor(Color.rgb(33, 33, 33));
        callOverlay.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Voz", 17);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView app = text("Ei Chat", 13);
        app.setTextColor(Color.rgb(155, 155, 155));
        app.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(app, new LinearLayout.LayoutParams(-2, dp(48)));
        callOverlay.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        callOverlay.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, .45f));

        callOrb = text("✦", 44);
        callOrb.setGravity(Gravity.CENTER);
        callOrb.setTextColor(Color.WHITE);
        callOrb.setBackground(circle(Color.rgb(39, 39, 39), Color.rgb(130, 130, 130), 3));
        callOrb.setElevation(dp(12));
        callOverlay.addView(callOrb, new LinearLayout.LayoutParams(dp(180), dp(180)));

        callStatus = text("Pronto", 24);
        callStatus.setTypeface(null, 1);
        callStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(50));
        statusParams.setMargins(0, dp(24), 0, 0);
        callOverlay.addView(callStatus, statusParams);

        callTranscript = text("Pode falar quando quiser.", 16);
        callTranscript.setGravity(Gravity.CENTER);
        callTranscript.setTextColor(Color.rgb(185, 185, 185));
        callTranscript.setMaxLines(4);
        callOverlay.addView(callTranscript, new LinearLayout.LayoutParams(dp(820), -2));

        callMicLabel = text("Microfone ativo", 12);
        callMicLabel.setGravity(Gravity.CENTER);
        callMicLabel.setTextColor(Color.rgb(130, 130, 130));
        LinearLayout.LayoutParams micLabelParams = new LinearLayout.LayoutParams(-1, dp(32));
        micLabelParams.setMargins(0, dp(8), 0, 0);
        callOverlay.addView(callMicLabel, micLabelParams);

        callOverlay.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, .55f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        callMute = button("Silenciar", Color.rgb(54, 54, 54), v -> toggleCallMute());
        LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(dp(150), dp(56));
        muteParams.setMargins(0, 0, dp(16), 0);
        controls.addView(callMute, muteParams);
        callEnd = button("Encerrar", Color.rgb(108, 43, 43), v -> endVoiceCall(false));
        controls.addView(callEnd, new LinearLayout.LayoutParams(dp(150), dp(56)));
        callOverlay.addView(controls, new LinearLayout.LayoutParams(-1, dp(64)));

        rootFrame.addView(callOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void toggleSidebar() {
        if (sidebar == null) return;
        if (sidebar.getVisibility() == View.VISIBLE) closeSidebar();
        else openSidebar();
    }

    private void openSidebar() {
        sidebarScrim.setVisibility(View.VISIBLE);
        sidebar.setVisibility(View.VISIBLE);
        sidebarFirst.requestFocus();
    }

    private void closeSidebar() {
        if (sidebarScrim != null) sidebarScrim.setVisibility(View.GONE);
        if (sidebar != null) sidebar.setVisibility(View.GONE);
        if (menuButton != null) menuButton.requestFocus();
    }

    private void showToolsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ferramentas")
                .setItems(new String[]{"Conversa por voz", "Assistente da TV", "Diagnóstico"}, (d, which) -> {
                    if (which == 0) startVoiceCall();
                    else if (which == 1) requestAssistantRole();
                    else if (which == 2) runDiagnostics();
                })
                .show();
    }

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        ChatHistoryStore.Conversation current = history.current();
        List<ChatHistoryStore.Conversation> recent = history.recent();
        for (ChatHistoryStore.Conversation conversation : recent) {
            String label = conversation.title == null || conversation.title.trim().isEmpty() ? "Novo chat" : conversation.title;
            int background = conversation.id.equals(current.id) ? Color.rgb(45, 45, 45) : Color.rgb(23, 23, 23);
            Button row = button(label, background, v -> selectConversation(conversation.id));
            row.setTextSize(13);
            row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(42));
            rowParams.setMargins(0, 0, 0, dp(2));
            historyList.addView(row, rowParams);
        }
    }

    private void renderCurrentConversation() {
        ChatHistoryStore.Conversation conversation = history.current();
        conversationTitle.setText(conversation.title == null ? "Novo chat" : conversation.title);
        chat.removeAllViews();
        if (conversation.messages.isEmpty()) {
            showWelcome();
        } else {
            for (ChatHistoryStore.Message message : conversation.messages) {
                appendMessageView(message.text, "user".equals(message.role));
            }
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showWelcome() {
        chat.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(120)));
        TextView mark = text("✦", 32);
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Color.rgb(232, 232, 232));
        chat.addView(mark, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView hero = text("Como posso ajudar?", 28);
        hero.setTypeface(null, 1);
        hero.setGravity(Gravity.CENTER);
        chat.addView(hero, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void appendMessageView(String value, boolean user) {
        if (value == null || value.trim().isEmpty()) return;
        int width = getResources().getDisplayMetrics().widthPixels;

        if (user) {
            TextView bubble = text(value.trim(), 16);
            bubble.setLineSpacing(0, 1.15f);
            bubble.setPadding(dp(17), dp(11), dp(17), dp(11));
            bubble.setBackground(shape(Color.rgb(55, 55, 55), 18, 0, 0));
            bubble.setMaxWidth((int) (width * .46f));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.gravity = Gravity.RIGHT;
            params.setMargins(dp(80), dp(8), 0, dp(12));
            chat.addView(bubble, params);
            return;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        TextView icon = text("✦", 15);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.rgb(28, 28, 28));
        icon.setBackground(circle(Color.rgb(238, 238, 238), 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.setMargins(0, dp(3), dp(13), 0);
        row.addView(icon, iconParams);

        TextView body = text(value.trim(), 16);
        body.setLineSpacing(dp(2), 1.14f);
        body.setTextColor(Color.rgb(238, 238, 238));
        body.setPadding(0, dp(4), 0, dp(4));
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, dp(6), dp(70), dp(16));
        chat.addView(row, rowParams);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void appendConversationMessage(String role, String value) {
        history.append(role, value);
        appendMessageView(value, "user".equals(role));
        conversationTitle.setText(history.current().title);
        renderHistory();
    }

    private void showNotice(String value) {
        if (value == null || value.trim().isEmpty()) return;
        TextView notice = text(value.trim(), 13);
        notice.setGravity(Gravity.CENTER);
        notice.setTextColor(Color.rgb(160, 160, 160));
        notice.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(100), dp(6), dp(100), dp(6));
        chat.addView(notice, params);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void newConversation() {
        if (callActive) endVoiceCall(false);
        destroySpeech();
        history.newConversation();
        renderHistory();
        renderCurrentConversation();
        input.setText("");
        closeSidebar();
        input.requestFocus();
    }

    private void selectConversation(String id) {
        if (callActive) endVoiceCall(false);
        destroySpeech();
        history.select(id);
        renderHistory();
        renderCurrentConversation();
        closeSidebar();
        input.requestFocus();
    }

    private void sendTyped() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) return;
        input.setText("");
        replyShouldSpeak = false;
        handle(query);
    }

    private void handle(String query) {
        if (query == null || query.trim().isEmpty()) return;
        String clean = query.trim();
        destroySpeech();
        appendConversationMessage("user", clean);
        if (callActive) setCallState("Pensando", clean, Color.rgb(180, 180, 180));

        String normalized = clean.toLowerCase(Locale.ROOT);
        if (callActive && (normalized.contains("encerrar chamada") || normalized.contains("finalizar chamada") || normalized.equals("encerrar"))) {
            appendConversationMessage("assistant", "Chamada de voz encerrada.");
            endVoiceCall(false);
            return;
        }

        CommandEngine.Result result = engine.execute(clean);
        if (result.handled) {
            appendConversationMessage("assistant", result.message);
            deliverSpeechIfNeeded(result.message);
            return;
        }

        JSONArray context = history.apiInput(20);
        ai.askConversation(context, new OpenAiClient.Callback() {
            @Override public void onSuccess(String answer) {
                runOnUiThread(() -> {
                    appendConversationMessage("assistant", answer);
                    if (callActive) setCallState("Respondendo", answer, Color.rgb(205, 205, 205));
                    deliverSpeechIfNeeded(answer);
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    appendConversationMessage("assistant", error);
                    if (callActive) {
                        callMute.setText("Falar");
                        callMicLabel.setText("Microfone em espera");
                        setCallState("Pronto", error, Color.rgb(185, 110, 110));
                    }
                    replyShouldSpeak = false;
                });
            }
        });
    }

    private void deliverSpeechIfNeeded(String answer) {
        boolean shouldSpeak = callActive || replyShouldSpeak;
        replyShouldSpeak = false;
        if (!shouldSpeak) return;
        speak(answer, afterReply());
    }

    private Runnable afterReply() {
        if (!callActive) return null;
        return () -> {
            if (!callActive || callMuted) return;
            setCallState("Ouvindo", "Pode continuar falando.", Color.rgb(165, 165, 165));
            new Handler(Looper.getMainLooper()).postDelayed(() -> startVoiceRecognition(true), 420);
        };
    }

    private void startSingleListening() {
        if (!auth.isConnected()) {
            connectChatGpt(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true;
            pendingVoiceCall = false;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        replyShouldSpeak = true;
        startVoiceRecognition(false);
    }

    private void startVoiceCall() {
        if (callActive) return;
        if (!auth.isConnected()) {
            connectChatGpt(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true;
            pendingVoiceCall = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }

        closeSidebar();
        callActive = true;
        callMuted = false;
        callListening = false;
        replyShouldSpeak = true;
        callMute.setText("Silenciar");
        callMicLabel.setText("Microfone ativo");
        callOverlay.setVisibility(View.VISIBLE);
        callEnd.requestFocus();
        setCallState("Ouvindo", "Pode falar normalmente.", Color.rgb(175, 175, 175));
        new Handler(Looper.getMainLooper()).postDelayed(() -> startVoiceRecognition(true), 350);
    }

    private void endVoiceCall(boolean announce) {
        callActive = false;
        callMuted = false;
        callListening = false;
        replyShouldSpeak = false;
        destroySpeech();
        if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
        afterSpeech = null;
        callOverlay.setVisibility(View.GONE);
        if (announce) showNotice("Chamada de voz encerrada.");
        callButton.requestFocus();
    }

    private void toggleCallMute() {
        if (!callActive) return;

        if (callMuted) {
            callMuted = false;
            callMute.setText("Silenciar");
            callMicLabel.setText("Microfone ativo");
            setCallState("Ouvindo", "Pode continuar falando.", Color.rgb(170, 170, 170));
            startVoiceRecognition(true);
            return;
        }

        if (!callListening) {
            callMute.setText("Silenciar");
            callMicLabel.setText("Microfone ativo");
            setCallState("Ouvindo", "Pode falar…", Color.rgb(170, 170, 170));
            startVoiceRecognition(true);
            return;
        }

        callMuted = true;
        destroySpeech();
        callMute.setText("Ativar microfone");
        callMicLabel.setText("Microfone silenciado");
        setCallState("Pausado", "O microfone está silenciado.", Color.rgb(130, 130, 130));
    }

    private void setCallState(String state, String transcript, int color) {
        if (callStatus != null) callStatus.setText(state);
        if (callTranscript != null && transcript != null) callTranscript.setText(transcript);
        if (callOrb != null) callOrb.setBackground(circle(Color.rgb(39, 39, 39), color, 4));
        if (callMicLabel != null && !callMuted) callMicLabel.setText(callListening ? "Microfone ativo" : "Microfone em espera");
    }

    private void startVoiceRecognition(boolean fromCall) {
        if (!auth.isConnected()) {
            if (fromCall) endVoiceCall(false);
            connectChatGpt(false);
            return;
        }
        if (fromCall && (!callActive || callMuted || callListening)) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (fromCall) {
                callMute.setText("Falar");
                setCallState("Voz indisponível", "A TV não disponibilizou o reconhecimento de voz.", Color.rgb(180, 100, 100));
            } else {
                showNotice("Esta TV não disponibilizou reconhecimento de voz para aplicativos.");
            }
            return;
        }

        if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            try {
                audio.setMode(AudioManager.MODE_NORMAL);
                audio.setSpeakerphoneOn(false);
            } catch (Throwable ignored) {}
        }

        destroySpeech();
        if (fromCall) {
            callListening = true;
            callMute.setText("Silenciar");
            callMicLabel.setText("Microfone ativo");
        }
        speech = VoiceRecognizerHelper.create(this);
        VoiceRecognizerHelper.record(this, "event", fromCall ? "call_startListening" : "startListening");

        speech.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                VoiceRecognizerHelper.record(MainActivity.this, "event", fromCall ? "call_ready" : "ready");
                if (fromCall) setCallState("Ouvindo", "Pode falar…", Color.rgb(185, 185, 185));
            }

            @Override public void onBeginningOfSpeech() {
                VoiceRecognizerHelper.record(MainActivity.this, "event", fromCall ? "call_speech_started" : "speech_started");
                if (fromCall) setCallState("Ouvindo", "Estou ouvindo você…", Color.rgb(205, 205, 205));
            }

            @Override public void onRmsChanged(float rms) {
                if (fromCall && callOrb != null) {
                    float scale = rms > 2f ? Math.min(1.16f, 1f + rms / 38f) : 1f;
                    callOrb.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
                }
            }

            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {
                VoiceRecognizerHelper.record(MainActivity.this, "event", fromCall ? "call_speech_ended" : "speech_ended");
                if (fromCall) setCallState("Entendendo", "Transformando sua fala em texto…", Color.rgb(170, 170, 170));
            }

            @Override public void onError(int error) {
                String code = VoiceRecognizerHelper.errorName(error) + " (" + error + ")";
                VoiceRecognizerHelper.record(MainActivity.this, "error", (fromCall ? "call_" : "") + code);
                destroySpeech();

                if (fromCall && callActive) {
                    callMute.setText("Falar");
                    callMicLabel.setText("Microfone em espera");
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT) {
                        setCallState("Pronto", "Não ouvi nada. Pressione “Falar” quando quiser continuar.", Color.rgb(150, 150, 150));
                    } else {
                        setCallState("Erro de voz", speechError(error) + " [" + code + "]", Color.rgb(185, 105, 105));
                    }
                    return;
                }

                showNotice("Reconhecimento de voz: " + speechError(error) + " [" + code + "].");
            }

            @Override public void onResults(Bundle results) {
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty() || values.get(0).trim().isEmpty()) {
                    onError(SpeechRecognizer.ERROR_NO_MATCH);
                    return;
                }
                String result = values.get(0).trim();
                VoiceRecognizerHelper.record(MainActivity.this, "result", (fromCall ? "call_" : "") + result);
                callListening = false;
                replyShouldSpeak = true;
                if (fromCall) setCallState("Pensando", result, Color.rgb(180, 180, 180));
                handle(result);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> values = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty()) return;
                String partial = values.get(0).trim();
                if (partial.isEmpty()) return;
                VoiceRecognizerHelper.record(MainActivity.this, "partial", (fromCall ? "call_" : "") + partial);
                if (fromCall) callTranscript.setText(partial);
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        try {
            speech.startListening(VoiceRecognizerHelper.intent());
        } catch (Throwable error) {
            destroySpeech();
            if (fromCall) {
                callMute.setText("Falar");
                setCallState("Falha ao abrir o microfone", String.valueOf(error.getMessage()), Color.rgb(185, 105, 105));
            } else {
                showNotice("Não foi possível iniciar o microfone: " + error.getMessage());
            }
        }
    }

    private void destroySpeech() {
        if (speech != null) {
            try { speech.cancel(); } catch (Throwable ignored) {}
            try { speech.destroy(); } catch (Throwable ignored) {}
            speech = null;
        }
        callListening = false;
    }

    private void speak(String value, Runnable after) {
        afterSpeech = after;
        if (tts == null || value == null || value.trim().isEmpty()) {
            finishSpeechCallback();
            return;
        }
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "ei_chat_reply");
    }

    private void finishSpeechCallback() {
        Runnable callback = afterSpeech;
        afterSpeech = null;
        if (callback != null) runOnUiThread(callback);
    }

    private void runDiagnostics() {
        closeSidebar();
        showNotice("Diagnóstico iniciado. O teste de áudio direto dura cerca de 2,5 segundos.");
        new AiVoiceRecorder(this).probeBuiltIn((ok, report) -> runOnUiThread(() -> {
            String data = TvMicrophone.report(this)
                    + "\nReconhecimento Android: " + (SpeechRecognizer.isRecognitionAvailable(this) ? "disponível" : "indisponível")
                    + "\n" + VoiceRecognizerHelper.diagnostic(this)
                    + "\nChatGPT: " + auth.accountLabel()
                    + "\n\n" + report;
            showNotice(data);
        }));
    }

    private void refreshAccount() {
        boolean connected = auth.isConnected();
        account.setText(connected ? "Conta" : "Conectar ChatGPT");
        accountState.setText(connected ? "ChatGPT • conectado" : "ChatGPT • não conectado");
        listen.setEnabled(connected);
        callButton.setEnabled(connected);
        listen.setAlpha(connected ? 1f : .45f);
        callButton.setAlpha(connected ? 1f : .45f);
    }

    private void accountAction() {
        if (!auth.isConnected()) {
            connectChatGpt(false);
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Conta ChatGPT")
                .setMessage(auth.accountLabel())
                .setItems(new String[]{"Trocar conta", "Sair da conta", "Cancelar"}, (d, which) -> {
                    if (which == 0) switchAccount();
                    else if (which == 1) confirmSignOut();
                    else d.dismiss();
                })
                .show();
    }

    private void confirmSignOut() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Sair da conta?")
                .setMessage("O Ei Chat vai apagar desta TV os tokens salvos da conta ChatGPT. O histórico local será mantido.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (d, w) -> signOutCurrentAccount())
                .show();
    }

    private void signOutCurrentAccount() {
        if (callActive) endVoiceCall(false);
        destroySpeech();
        auth.signOut();
        ai = new OpenAiClient(this);
        refreshAccount();
        closeSidebar();
        showNotice("Conta ChatGPT desconectada. Seu histórico local continua salvo nesta TV.");
    }

    private void switchAccount() {
        if (callActive) endVoiceCall(false);
        destroySpeech();
        auth.signOut();
        ai = new OpenAiClient(this);
        refreshAccount();
        closeSidebar();
        showNotice("Conta atual desconectada. Escolha outra conta no navegador.");
        connectChatGpt(true);
    }

    private void connectChatGpt(boolean chooseAccount) {
        closeSidebar();
        showNotice(chooseAccount ? "Abrindo o seletor de contas no navegador." : "Abrindo o login do ChatGPT no navegador.");
        account.setEnabled(false);
        auth.signIn(chooseAccount, new ChatGptAuth.Callback() {
            @Override public void onSuccess(String message) {
                runOnUiThread(() -> {
                    ai = new OpenAiClient(MainActivity.this);
                    account.setEnabled(true);
                    refreshAccount();
                    showNotice(message + " Agora você pode conversar por texto ou voz.");
                    input.requestFocus();
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    account.setEnabled(true);
                    refreshAccount();
                    showNotice(error);
                    menuButton.requestFocus();
                });
            }
        });
    }

    private void requestAssistantRole() {
        closeSidebar();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager manager = (RoleManager) getSystemService(ROLE_SERVICE);
                if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQ_ASSISTANT);
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Throwable error) {
            showNotice("A TV não abriu a tela de assistente automaticamente. Abra Configurações > Apps > Assistente padrão.");
        }
    }

    private static String speechError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "erro de áudio da TV";
            case SpeechRecognizer.ERROR_CLIENT: return "erro do cliente";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "sem permissão de microfone";
            case SpeechRecognizer.ERROR_NETWORK: return "erro de rede";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "tempo de rede esgotado";
            case SpeechRecognizer.ERROR_NO_MATCH: return "não entendi a fala";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "reconhecedor ocupado";
            case SpeechRecognizer.ERROR_SERVER: return "erro do serviço de voz";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "nenhuma fala detectada";
            default: return "código " + error;
        }
    }

    @Override
    public void onBackPressed() {
        if (callActive || callOverlay.getVisibility() == View.VISIBLE) {
            endVoiceCall(false);
            return;
        }
        if (sidebar != null && sidebar.getVisibility() == View.VISIBLE) {
            closeSidebar();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackAt <= 2000L) {
            destroySpeech();
            if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
            finishAndRemoveTask();
            return;
        }
        lastBackAt = now;
        Toast.makeText(this, "Pressione Voltar novamente para sair do Ei Chat", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_MIC && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && pendingPermission) {
            boolean startCall = pendingVoiceCall;
            pendingPermission = false;
            pendingVoiceCall = false;
            if (startCall) startVoiceCall();
            else startSingleListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth != null) refreshAccount();
    }

    @Override
    protected void onDestroy() {
        callActive = false;
        destroySpeech();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
