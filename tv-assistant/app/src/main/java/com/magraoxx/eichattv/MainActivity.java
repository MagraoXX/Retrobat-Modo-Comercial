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
    private LinearLayout chat, historyList, callOverlay;
    private ScrollView chatScroll;
    private TextView caption, micText, accountState, conversationTitle;
    private TextView callOrb, callStatus, callTranscript, callMicLabel;
    private EditText input;
    private Button listen, account, callButton, callMute, callEnd;

    private TextToSpeech tts;
    private CommandEngine engine;
    private OpenAiClient ai;
    private ChatGptAuth auth;
    private ChatHistoryStore history;
    private SpeechRecognizer speech;

    private boolean pendingPermission, pendingVoiceCall, audioRetry, callActive, callMuted;
    private int voiceRetryCount;
    private Runnable afterSpeech;
    private long lastBackAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        engine = new CommandEngine(this);
        ai = new OpenAiClient(this);
        auth = new ChatGptAuth(this);
        history = new ChatHistoryStore(this);
        buildUi();
        refreshAccount();
        renderHistory();
        renderCurrentConversation();

        tts = new TextToSpeech(this, x -> {
            if (x == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.02f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> { if (callActive) setCallState("Respondendo", "Ei Chat está falando…", Color.rgb(210,210,210)); });
                    }
                    @Override public void onDone(String id) { finishSpeechCallback(); }
                    @Override public void onError(String id) { finishSpeechCallback(); }
                });
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);

        if (getIntent() != null && getIntent().getBooleanExtra("start_listening", false))
            new Handler(Looper.getMainLooper()).postDelayed(this::startSingleListening, 450);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, float size) {
        TextView v = new TextView(this); v.setText(s); v.setTextColor(Color.rgb(236,236,236)); v.setTextSize(size); return v;
    }
    private GradientDrawable shape(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(strokeWidth), stroke); return g;
    }
    private GradientDrawable circle(int fill, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(strokeWidth), stroke); return g;
    }
    private int lighten(int c) {
        return Color.rgb(Math.min(255, Color.red(c)+22), Math.min(255, Color.green(c)+22), Math.min(255, Color.blue(c)+22));
    }
    private void paintButton(Button b, int color, boolean focused) {
        b.setBackground(shape(focused ? lighten(color) : color, 13, focused ? Color.rgb(220,220,220) : 0, 2));
        b.setScaleX(focused ? 1.02f : 1f); b.setScaleY(focused ? 1.04f : 1f); b.setElevation(focused ? dp(8) : dp(1));
    }
    private Button button(String label, int color, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(12),0,dp(12),0); b.setMinHeight(dp(48)); b.setFocusable(true); b.setFocusableInTouchMode(true);
        b.setOnClickListener(listener); paintButton(b,color,false); b.setOnFocusChangeListener((v,f)->paintButton((Button)v,color,f)); return b;
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this); rootFrame.setBackgroundColor(Color.rgb(33,33,33));
        LinearLayout app = new LinearLayout(this); app.setOrientation(LinearLayout.HORIZONTAL); app.setBackgroundColor(Color.rgb(33,33,33));
        rootFrame.addView(app,new FrameLayout.LayoutParams(-1,-1));
        buildSidebar(app); buildChatArea(app); buildCallOverlay(); setContentView(rootFrame);
    }

    private void buildSidebar(LinearLayout app) {
        LinearLayout side = new LinearLayout(this); side.setOrientation(LinearLayout.VERTICAL); side.setPadding(dp(18),dp(18),dp(18),dp(16));
        side.setBackgroundColor(Color.rgb(23,23,23)); app.addView(side,new LinearLayout.LayoutParams(dp(330),-1));

        LinearLayout brandRow = new LinearLayout(this); brandRow.setOrientation(LinearLayout.HORIZONTAL); brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("✦",24); mark.setGravity(Gravity.CENTER); mark.setBackground(circle(Color.rgb(245,245,245),0,0)); mark.setTextColor(Color.rgb(20,20,20));
        brandRow.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView brand = text("Ei Chat",22); brand.setTypeface(null,1); brand.setGravity(Gravity.CENTER_VERTICAL); brand.setPadding(dp(12),0,0,0);
        brandRow.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1f)); side.addView(brandRow,new LinearLayout.LayoutParams(-1,dp(50)));

        Button newChat = button("＋  Novo chat",Color.rgb(45,45,45),v->newConversation());
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-1,dp(52)); newLp.setMargins(0,dp(12),0,dp(12)); side.addView(newChat,newLp);
        TextView historyLabel = text("Chats",12); historyLabel.setTextColor(Color.rgb(150,150,150)); historyLabel.setPadding(dp(4),0,0,dp(6));
        side.addView(historyLabel,new LinearLayout.LayoutParams(-1,dp(28)));

        ScrollView historyScroll = new ScrollView(this); historyList = new LinearLayout(this); historyList.setOrientation(LinearLayout.VERTICAL);
        historyScroll.addView(historyList,new ScrollView.LayoutParams(-1,-2)); side.addView(historyScroll,new LinearLayout.LayoutParams(-1,0,1f));

        TextView toolsLabel = text("Ferramentas",12); toolsLabel.setTextColor(Color.rgb(145,145,145)); toolsLabel.setPadding(dp(4),dp(8),0,dp(4));
        side.addView(toolsLabel,new LinearLayout.LayoutParams(-1,dp(34)));
        Button assistant = button("Assistente da TV",Color.rgb(38,38,38),v->requestAssistantRole());
        LinearLayout.LayoutParams toolLp = new LinearLayout.LayoutParams(-1,dp(48)); toolLp.setMargins(0,0,0,dp(6)); side.addView(assistant,toolLp);
        Button diag = button("Diagnóstico",Color.rgb(38,38,38),v->runDiagnostics());
        LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(-1,dp(48)); diagLp.setMargins(0,0,0,dp(12)); side.addView(diag,diagLp);

        accountState = text("ChatGPT",12); accountState.setTextColor(Color.rgb(160,160,160)); accountState.setMaxLines(1);
        side.addView(accountState,new LinearLayout.LayoutParams(-1,dp(26)));
        account = button("Conectar ChatGPT",Color.rgb(45,45,45),v->accountAction()); side.addView(account,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private void buildChatArea(LinearLayout app) {
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(dp(34),dp(16),dp(34),dp(18));
        app.addView(main,new LinearLayout.LayoutParams(0,-1,1f));

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        conversationTitle = text("Novo chat",17); conversationTitle.setTypeface(null,1); conversationTitle.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(conversationTitle,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout stateCol = new LinearLayout(this); stateCol.setOrientation(LinearLayout.VERTICAL); stateCol.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        caption = text("Ei Chat",13); caption.setGravity(Gravity.RIGHT); caption.setTextColor(Color.rgb(190,190,190));
        micText = text("● preparando",12); micText.setGravity(Gravity.RIGHT); micText.setTextColor(Color.rgb(140,140,140));
        stateCol.addView(caption,new LinearLayout.LayoutParams(-2,dp(22))); stateCol.addView(micText,new LinearLayout.LayoutParams(-2,dp(22)));
        top.addView(stateCol,new LinearLayout.LayoutParams(-2,dp(48))); main.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));

        chatScroll = new ScrollView(this); chatScroll.setFillViewport(true); chat = new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(dp(70),dp(12),dp(70),dp(30)); chatScroll.addView(chat,new ScrollView.LayoutParams(-1,-2));
        main.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout composerWrap = new LinearLayout(this); composerWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout composer = new LinearLayout(this); composer.setOrientation(LinearLayout.HORIZONTAL); composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(14),dp(8),dp(9),dp(8)); composer.setBackground(shape(Color.rgb(47,47,47),24,Color.rgb(73,73,73),1));
        composerWrap.addView(composer,new LinearLayout.LayoutParams(dp(980),-2));

        input = new EditText(this); input.setHint("Envie uma mensagem para o Ei Chat"); input.setHintTextColor(Color.rgb(150,150,150));
        input.setTextColor(Color.WHITE); input.setTextSize(17); input.setBackgroundColor(Color.TRANSPARENT); input.setPadding(dp(10),dp(5),dp(10),dp(5));
        input.setMaxLines(4); input.setMinLines(1); input.setFocusable(true); composer.addView(input,new LinearLayout.LayoutParams(0,-2,1f));

        listen = button("🎤",Color.rgb(61,61,61),v->startSingleListening());
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(56),dp(52)); micLp.setMargins(dp(6),0,dp(6),0); composer.addView(listen,micLp);
        callButton = button("◉  Voz",Color.rgb(61,61,61),v->startVoiceCall());
        LinearLayout.LayoutParams callLp = new LinearLayout.LayoutParams(dp(98),dp(52)); callLp.setMargins(0,0,dp(6),0); composer.addView(callButton,callLp);
        Button send = button("↑",Color.rgb(238,238,238),v->sendTyped()); send.setTextColor(Color.BLACK); composer.addView(send,new LinearLayout.LayoutParams(dp(56),dp(52)));

        main.addView(composerWrap,new LinearLayout.LayoutParams(-1,-2));
        TextView hint = text("Use o controle para navegar • Voz inicia conversa contínua",11); hint.setTextColor(Color.rgb(115,115,115)); hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1,dp(28)); hintLp.setMargins(0,dp(5),0,0); main.addView(hint,hintLp);
    }

    private void buildCallOverlay() {
        callOverlay = new LinearLayout(this); callOverlay.setOrientation(LinearLayout.VERTICAL); callOverlay.setGravity(Gravity.CENTER_HORIZONTAL);
        callOverlay.setPadding(dp(48),dp(30),dp(48),dp(36)); callOverlay.setBackgroundColor(Color.rgb(18,18,18)); callOverlay.setVisibility(View.GONE);

        LinearLayout callTop = new LinearLayout(this); callTop.setOrientation(LinearLayout.HORIZONTAL); callTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Ei Chat",20); title.setTypeface(null,1); callTop.addView(title,new LinearLayout.LayoutParams(0,dp(52),1f));
        TextView mode = text("Conversa por voz",13); mode.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); mode.setTextColor(Color.rgb(155,155,155));
        callTop.addView(mode,new LinearLayout.LayoutParams(-2,dp(52))); callOverlay.addView(callTop,new LinearLayout.LayoutParams(-1,dp(56)));

        callOverlay.addView(new Space(this),new LinearLayout.LayoutParams(1,0,.45f));
        callOrb = text("✦",54); callOrb.setGravity(Gravity.CENTER); callOrb.setTextColor(Color.WHITE);
        callOrb.setBackground(circle(Color.rgb(40,40,40),Color.rgb(120,120,120),3)); callOrb.setElevation(dp(14));
        callOverlay.addView(callOrb,new LinearLayout.LayoutParams(dp(224),dp(224)));

        callStatus = text("Pronto",28); callStatus.setTypeface(null,1); callStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1,dp(52)); stLp.setMargins(0,dp(28),0,0); callOverlay.addView(callStatus,stLp);
        callTranscript = text("Pode falar quando quiser.",17); callTranscript.setGravity(Gravity.CENTER); callTranscript.setTextColor(Color.rgb(185,185,185)); callTranscript.setMaxLines(3);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(dp(900),-2); trLp.setMargins(0,dp(8),0,0); callOverlay.addView(callTranscript,trLp);
        callMicLabel = text("Microfone ativo",12); callMicLabel.setGravity(Gravity.CENTER); callMicLabel.setTextColor(Color.rgb(130,130,130));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1,dp(32)); mlp.setMargins(0,dp(8),0,0); callOverlay.addView(callMicLabel,mlp);

        callOverlay.addView(new Space(this),new LinearLayout.LayoutParams(1,0,.55f));
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER);
        callMute = button("Silenciar",Color.rgb(54,54,54),v->toggleCallMute());
        LinearLayout.LayoutParams muteLp = new LinearLayout.LayoutParams(dp(150),dp(58)); muteLp.setMargins(0,0,dp(18),0); controls.addView(callMute,muteLp);
        callEnd = button("Encerrar",Color.rgb(116,45,45),v->endVoiceCall(false)); controls.addView(callEnd,new LinearLayout.LayoutParams(dp(150),dp(58)));
        callOverlay.addView(controls,new LinearLayout.LayoutParams(-1,dp(66))); rootFrame.addView(callOverlay,new FrameLayout.LayoutParams(-1,-1));
    }

    private void renderHistory() {
        if (historyList == null) return; historyList.removeAllViews(); ChatHistoryStore.Conversation current = history.current();
        List<ChatHistoryStore.Conversation> recent = history.recent();
        for (ChatHistoryStore.Conversation c : recent) {
            String label = c.title == null || c.title.trim().isEmpty() ? "Novo chat" : c.title;
            int base = c.id.equals(current.id) ? Color.rgb(48,48,48) : Color.rgb(23,23,23);
            Button b = button(label,base,v->selectConversation(c.id)); b.setTextSize(14); b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); b.setPadding(dp(12),0,dp(10),0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(46)); lp.setMargins(0,0,0,dp(3)); historyList.addView(b,lp);
        }
    }

    private void renderCurrentConversation() {
        ChatHistoryStore.Conversation c = history.current(); conversationTitle.setText(c.title == null ? "Novo chat" : c.title); chat.removeAllViews();
        if (c.messages.isEmpty()) showWelcome(); else for (ChatHistoryStore.Message m : c.messages) appendMessageView(m.text,"user".equals(m.role));
        chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showWelcome() {
        chat.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(120)));
        TextView mark = text("✦",36); mark.setGravity(Gravity.CENTER); mark.setTextColor(Color.rgb(230,230,230)); chat.addView(mark,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView hero = text("Como posso ajudar?",30); hero.setTypeface(null,1); hero.setGravity(Gravity.CENTER); chat.addView(hero,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView sub = text("Converse, pergunte, controle a TV ou inicie uma chamada por voz.",15); sub.setTextColor(Color.rgb(155,155,155)); sub.setGravity(Gravity.CENTER);
        chat.addView(sub,new LinearLayout.LayoutParams(-1,dp(46)));
    }

    private void appendMessageView(String value, boolean user) {
        if (value == null || value.trim().isEmpty()) return;
        if (user) {
            TextView bubble = text(value.trim(),16); bubble.setLineSpacing(0,1.15f); bubble.setPadding(dp(18),dp(12),dp(18),dp(12));
            bubble.setBackground(shape(Color.rgb(55,55,55),18,0,0)); bubble.setMaxWidth(dp(760));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,-2); lp.gravity=Gravity.RIGHT; lp.setMargins(dp(160),dp(8),0,dp(12)); chat.addView(bubble,lp);
        } else {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP);
            TextView icon = text("✦",16); icon.setGravity(Gravity.CENTER); icon.setTextColor(Color.rgb(25,25,25)); icon.setBackground(circle(Color.rgb(238,238,238),0,0));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(34),dp(34)); iconLp.setMargins(0,dp(4),dp(14),0); row.addView(icon,iconLp);
            TextView body = text(value.trim(),16); body.setLineSpacing(dp(2),1.14f); body.setTextColor(Color.rgb(235,235,235)); body.setPadding(0,dp(5),0,dp(5));
            row.addView(body,new LinearLayout.LayoutParams(0,-2,1f)); LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1,-2); rowLp.setMargins(0,dp(6),dp(120),dp(16)); chat.addView(row,rowLp);
        }
        chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void appendConversationMessage(String role, String value) {
        history.append(role,value); appendMessageView(value,"user".equals(role)); conversationTitle.setText(history.current().title); renderHistory();
    }

    private void showNotice(String value) {
        if (value == null || value.trim().isEmpty()) return; TextView notice = text(value.trim(),13); notice.setGravity(Gravity.CENTER);
        notice.setTextColor(Color.rgb(165,165,165)); notice.setPadding(dp(16),dp(8),dp(16),dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(120),dp(7),dp(120),dp(7)); chat.addView(notice,lp);
        chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void newConversation() {
        if (callActive) endVoiceCall(false); destroySpeech(); history.newConversation(); renderHistory(); renderCurrentConversation(); input.setText("");
        caption.setText("Ei Chat"); micText.setText(auth.isConnected()?"● IA pronta":"● conectar ChatGPT"); input.requestFocus();
    }
    private void selectConversation(String id) {
        if (callActive) endVoiceCall(false); destroySpeech(); history.select(id); renderHistory(); renderCurrentConversation(); input.requestFocus();
    }
    private void sendTyped() { String q=input.getText().toString().trim(); if(q.isEmpty())return; input.setText(""); handle(q); }

    private void handle(String q) {
        if (q == null || q.trim().isEmpty()) return;
        String clean=q.trim(); destroySpeech(); appendConversationMessage("user",clean); caption.setText("Ei Chat"); micText.setText("● processando");
        if (callActive) setCallState("Pensando",clean,Color.rgb(180,180,180));

        String n=clean.toLowerCase(Locale.ROOT);
        if (callActive && (n.contains("encerrar chamada") || n.contains("finalizar chamada") || n.equals("encerrar"))) {
            appendConversationMessage("assistant","Chamada de voz encerrada."); endVoiceCall(false); return;
        }

        CommandEngine.Result r=engine.execute(clean);
        if (r.handled) {
            appendConversationMessage("assistant",r.message); micText.setText("● comando executado"); speak(r.message,afterReply()); return;
        }

        caption.setText("Ei Chat • pensando"); JSONArray context=history.apiInput(20);
        ai.askConversation(context,new OpenAiClient.Callback(){
            @Override public void onSuccess(String answer){ runOnUiThread(()->{
                caption.setText("Ei Chat"); appendConversationMessage("assistant",answer); micText.setText(callActive?"● respondendo":"● IA pronta");
                if(callActive)setCallState("Respondendo",answer,Color.rgb(205,205,205)); speak(answer,afterReply());
            }); }
            @Override public void onError(String error){ runOnUiThread(()->{
                caption.setText("Ei Chat • erro da IA"); appendConversationMessage("assistant",error); micText.setText(callActive?"● chamada ativa":"● tente novamente");
                if(callActive){ setCallState("Não consegui responder",error,Color.rgb(190,120,120));
                    new Handler(Looper.getMainLooper()).postDelayed(()->{if(callActive&&!callMuted)startVoiceRecognition(true);},900); }
            }); }
        });
    }

    private Runnable afterReply() {
        if (!callActive) return null;
        return ()->{ if(callActive&&!callMuted){ setCallState("Ouvindo","Pode continuar falando.",Color.rgb(160,160,160));
            new Handler(Looper.getMainLooper()).postDelayed(()->startVoiceRecognition(true),260); } };
    }

    private void startSingleListening() {
        if(!auth.isConnected()){connectChatGpt(false);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingPermission=true; pendingVoiceCall=false; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC); return;
        }
        audioRetry=false; voiceRetryCount=0; startVoiceRecognition(false);
    }

    private void startVoiceCall() {
        if(callActive)return;
        if(!auth.isConnected()){connectChatGpt(false);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingPermission=true; pendingVoiceCall=true; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC); return;
        }
        callActive=true; callMuted=false; voiceRetryCount=0; callMute.setText("Silenciar"); callOverlay.setVisibility(View.VISIBLE); callEnd.requestFocus();
        caption.setText("Ei Chat • chamada de voz"); micText.setText("● chamada ativa");
        setCallState("Ouvindo","Pode falar normalmente. Eu continuo ouvindo depois de cada resposta.",Color.rgb(170,170,170));
        new Handler(Looper.getMainLooper()).postDelayed(()->startVoiceRecognition(true),280);
    }

    private void endVoiceCall(boolean announce) {
        callActive=false; callMuted=false; destroySpeech(); if(tts!=null)try{tts.stop();}catch(Throwable ignored){} afterSpeech=null;
        callOverlay.setVisibility(View.GONE); caption.setText("Ei Chat"); micText.setText(auth.isConnected()?"● IA pronta":"● conectar ChatGPT");
        if(announce)showNotice("Chamada de voz encerrada."); callButton.requestFocus();
    }

    private void toggleCallMute() {
        if(!callActive)return; callMuted=!callMuted;
        if(callMuted){ destroySpeech(); callMute.setText("Ativar microfone"); callMicLabel.setText("Microfone silenciado");
            setCallState("Pausado","O microfone está silenciado.",Color.rgb(130,130,130));
        } else { callMute.setText("Silenciar"); callMicLabel.setText("Microfone ativo");
            setCallState("Ouvindo","Pode continuar falando.",Color.rgb(170,170,170)); startVoiceRecognition(true); }
    }

    private void setCallState(String state,String transcript,int color) {
        if(callStatus!=null)callStatus.setText(state); if(callTranscript!=null&&transcript!=null)callTranscript.setText(transcript);
        if(callOrb!=null)callOrb.setBackground(circle(Color.rgb(38,38,38),color,4));
        if(callMicLabel!=null&&!callMuted)callMicLabel.setText(callActive?"Microfone ativo":"Microfone inativo");
    }

    private void startVoiceRecognition(boolean fromCall) {
        if(!auth.isConnected()){if(fromCall)endVoiceCall(false);connectChatGpt(false);return;}
        if(fromCall&&(!callActive||callMuted))return;
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            caption.setText("Ei Chat • voz indisponível");
            if(fromCall)setCallState("Voz indisponível","A TV não disponibilizou o reconhecimento de voz.",Color.rgb(180,100,100));
            else showNotice("Esta TV não disponibilizou um serviço de reconhecimento de voz para aplicativos."); return;
        }
        if(tts!=null)try{tts.stop();}catch(Throwable ignored){}
        AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); if(am!=null)try{am.setMode(AudioManager.MODE_NORMAL);am.setSpeakerphoneOn(false);}catch(Throwable ignored){}
        destroySpeech(); speech=VoiceRecognizerHelper.create(this); VoiceRecognizerHelper.record(this,"event",fromCall?"call_startListening":"startListening");
        speech.setRecognitionListener(new RecognitionListener(){
            @Override public void onReadyForSpeech(Bundle p){ audioRetry=false; VoiceRecognizerHelper.record(MainActivity.this,"event",fromCall?"call_ready":"ready");
                caption.setText("Ei Chat • ouvindo"); micText.setText("● ouvindo"); if(fromCall)setCallState("Ouvindo","Pode falar…",Color.rgb(185,185,185)); }
            @Override public void onBeginningOfSpeech(){ VoiceRecognizerHelper.record(MainActivity.this,"event",fromCall?"call_speech_started":"speech_started");
                if(fromCall)setCallState("Ouvindo","Estou ouvindo você…",Color.rgb(205,205,205)); }
            @Override public void onRmsChanged(float rms){ micText.setText(rms>2f?"● ouvindo sua voz":"● microfone ativo");
                if(fromCall&&callOrb!=null){float scale=rms>2f?Math.min(1.18f,1f+rms/35f):1f;callOrb.animate().scaleX(scale).scaleY(scale).setDuration(90).start();} }
            @Override public void onBufferReceived(byte[] b){}
            @Override public void onEndOfSpeech(){ VoiceRecognizerHelper.record(MainActivity.this,"event",fromCall?"call_speech_ended":"speech_ended");
                caption.setText("Ei Chat • entendendo"); micText.setText("● entendendo"); if(fromCall)setCallState("Entendendo","Transformando sua fala em texto…",Color.rgb(170,170,170)); }
            @Override public void onError(int e){
                String code=VoiceRecognizerHelper.errorName(e)+" ("+e+")"; VoiceRecognizerHelper.record(MainActivity.this,"error",(fromCall?"call_":"")+code); destroySpeech();
                if(e==SpeechRecognizer.ERROR_AUDIO&&!audioRetry){ audioRetry=true; caption.setText("Ei Chat • ajustando áudio"); micText.setText("● segunda tentativa");
                    if(fromCall)setCallState("Ajustando microfone","Tentando novamente…",Color.rgb(165,165,165));
                    new Handler(Looper.getMainLooper()).postDelayed(()->startVoiceRecognition(fromCall),650); return; }
                if(fromCall&&callActive&&!callMuted&&(e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT||e==SpeechRecognizer.ERROR_CLIENT)){
                    setCallState("Ouvindo","Não entendi. Pode repetir…",Color.rgb(165,165,165));
                    new Handler(Looper.getMainLooper()).postDelayed(()->{if(callActive&&!callMuted)startVoiceRecognition(true);},700); return; }
                if(!fromCall&&(e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)&&voiceRetryCount<1){
                    voiceRetryCount++; caption.setText("Ei Chat • ouvindo novamente"); micText.setText("● pode falar");
                    new Handler(Looper.getMainLooper()).postDelayed(()->startVoiceRecognition(false),450); return; }
                caption.setText("Ei Chat • erro de voz"); micText.setText("● tente novamente");
                if(fromCall)setCallState("Erro de voz",speechError(e)+" ["+code+"]",Color.rgb(185,105,105)); else showNotice("Reconhecimento de voz: "+speechError(e)+" ["+code+"].");
            }
            @Override public void onResults(Bundle b){ ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(a==null||a.isEmpty()||a.get(0).trim().isEmpty()){onError(SpeechRecognizer.ERROR_NO_MATCH);return;}
                String result=a.get(0).trim(); VoiceRecognizerHelper.record(MainActivity.this,"result",(fromCall?"call_":"")+result); voiceRetryCount=0;
                if(fromCall)setCallState("Pensando",result,Color.rgb(180,180,180)); handle(result); }
            @Override public void onPartialResults(Bundle b){ ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a==null||a.isEmpty())return;
                String partial=a.get(0).trim(); if(partial.isEmpty())return; VoiceRecognizerHelper.record(MainActivity.this,"partial",(fromCall?"call_":"")+partial);
                if(fromCall)callTranscript.setText(partial); else micText.setText("● "+(partial.length()>34?partial.substring(0,34)+"…":partial)); }
            @Override public void onEvent(int t,Bundle b){}
        });
        try{speech.startListening(VoiceRecognizerHelper.intent());}
        catch(Throwable e){ VoiceRecognizerHelper.record(this,"error","start_exception: "+e.getMessage()); caption.setText("Ei Chat • erro de voz");
            if(fromCall)setCallState("Falha ao abrir o microfone",String.valueOf(e.getMessage()),Color.rgb(185,105,105)); else showNotice("Não foi possível iniciar o microfone: "+e.getMessage()); }
    }

    private void destroySpeech(){ if(speech!=null){try{speech.cancel();}catch(Throwable ignored){}try{speech.destroy();}catch(Throwable ignored){}speech=null;} }
    private void speak(String value,Runnable after){ afterSpeech=after; if(tts==null||value==null||value.trim().isEmpty()){finishSpeechCallback();return;} tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"ei_chat_reply"); }
    private void finishSpeechCallback(){ Runnable r=afterSpeech; afterSpeech=null; if(r!=null)runOnUiThread(r); }

    private void runDiagnostics(){
        caption.setText("Diagnóstico • microfone"); micText.setText("● verificando"); showNotice("Diagnóstico iniciado. O teste de áudio direto dura cerca de 2,5 segundos.");
        new AiVoiceRecorder(this).probeBuiltIn((ok,report)->runOnUiThread(()->{
            String data=TvMicrophone.report(this)+"\nReconhecimento Android: "+(SpeechRecognizer.isRecognitionAvailable(this)?"disponível":"indisponível")+
                    "\n"+VoiceRecognizerHelper.diagnostic(this)+"\nChatGPT: "+auth.accountLabel()+"\n\n"+report;
            showNotice(data); micText.setText(ok?"● captura OK":"● captura falhou"); caption.setText("Ei Chat");
        }));
    }

    private void refreshAccount(){
        boolean ok=auth.isConnected(); account.setText(ok?"Gerenciar conta":"Conectar ChatGPT"); accountState.setText(ok?"ChatGPT • "+auth.accountLabel():"ChatGPT • não conectado");
        listen.setEnabled(ok); callButton.setEnabled(ok); listen.setAlpha(ok?1f:.45f); callButton.setAlpha(ok?1f:.45f); micText.setText(ok?"● IA pronta":"● conectar ChatGPT");
    }

    private void accountAction(){
        if(!auth.isConnected()){connectChatGpt(false);return;}
        new android.app.AlertDialog.Builder(this).setTitle("Conta ChatGPT").setMessage(auth.accountLabel())
                .setItems(new String[]{"Trocar conta","Sair da conta","Cancelar"},(d,which)->{if(which==0)switchAccount();else if(which==1)confirmSignOut();else d.dismiss();}).show();
    }

    private void confirmSignOut(){
        new android.app.AlertDialog.Builder(this).setTitle("Sair da conta?")
                .setMessage("O Ei Chat vai apagar desta TV os tokens salvos da conta ChatGPT. O histórico local do Ei Chat será mantido.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Sair",(d,w)->signOutCurrentAccount()).show();
    }

    private void signOutCurrentAccount(){
        if(callActive)endVoiceCall(false); destroySpeech(); auth.signOut(); ai=new OpenAiClient(this); refreshAccount();
        showNotice("Conta ChatGPT desconectada. Seu histórico local continua salvo nesta TV."); account.requestFocus();
    }

    private void switchAccount(){
        if(callActive)endVoiceCall(false); destroySpeech(); auth.signOut(); ai=new OpenAiClient(this); refreshAccount();
        showNotice("Conta atual desconectada. Escolha outra conta no navegador."); connectChatGpt(true);
    }

    private void connectChatGpt(boolean chooseAccount){
        caption.setText(chooseAccount?"Trocando conta ChatGPT":"Conectando ao ChatGPT"); showNotice(chooseAccount?"Abrindo o seletor de contas no navegador.":"Abrindo o login do ChatGPT no navegador.");
        account.setEnabled(false); auth.signIn(chooseAccount,new ChatGptAuth.Callback(){
            @Override public void onSuccess(String m){runOnUiThread(()->{ai=new OpenAiClient(MainActivity.this);account.setEnabled(true);refreshAccount();showNotice(m+" Agora você pode conversar por texto ou voz.");input.requestFocus();});}
            @Override public void onError(String e){runOnUiThread(()->{account.setEnabled(true);caption.setText("Ei Chat");refreshAccount();showNotice(e);account.requestFocus();});}
        });
    }

    private void requestAssistantRole(){
        try{
            if(Build.VERSION.SDK_INT>=29){RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE); if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)){
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),REQ_ASSISTANT);return;}}
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        }catch(Throwable e){showNotice("A TV não abriu a tela de assistente automaticamente. Abra Configurações > Apps > Assistente padrão.");}
    }

    private static String speechError(int e){
        switch(e){
            case SpeechRecognizer.ERROR_AUDIO:return "erro de áudio da TV";
            case SpeechRecognizer.ERROR_CLIENT:return "erro do cliente";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "sem permissão de microfone";
            case SpeechRecognizer.ERROR_NETWORK:return "erro de rede";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "tempo de rede esgotado";
            case SpeechRecognizer.ERROR_NO_MATCH:return "não entendi a fala";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "reconhecedor ocupado";
            case SpeechRecognizer.ERROR_SERVER:return "erro do serviço de voz";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "nenhuma fala detectada";
            default:return "código "+e;
        }
    }

    @Override public void onBackPressed(){
        if(callActive||callOverlay.getVisibility()==View.VISIBLE){endVoiceCall(false);return;}
        long now=System.currentTimeMillis(); if(now-lastBackAt<=2000L){destroySpeech();if(tts!=null)try{tts.stop();}catch(Throwable ignored){}finishAndRemoveTask();return;}
        lastBackAt=now; Toast.makeText(this,"Pressione Voltar novamente para sair do Ei Chat",Toast.LENGTH_SHORT).show();
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==REQ_MIC&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&pendingPermission){boolean call=pendingVoiceCall;pendingPermission=false;pendingVoiceCall=false;if(call)startVoiceCall();else startSingleListening();}
    }

    @Override protected void onResume(){super.onResume();if(auth!=null)refreshAccount();}
    @Override protected void onDestroy(){callActive=false;destroySpeech();if(tts!=null)tts.shutdown();super.onDestroy();}
}
