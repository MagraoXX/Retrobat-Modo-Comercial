package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQ_MIC=1001;
    private TextView status, setupState;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private CommandEngine commandEngine;
    private OpenAiClient aiClient;
    private ChatGptAuth chatGptAuth;
    private boolean pendingListen;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        commandEngine=new CommandEngine(this); aiClient=new OpenAiClient(this); chatGptAuth=new ChatGptAuth(this);
        buildUi(); initTts(); initSpeechRecognizer();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Etapa 1 de 3 — permita o uso do microfone para continuar.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
        } else refreshSetupState();
        if(getIntent()!=null&&getIntent().getBooleanExtra("start_listening",false))startListening();
    }

    @Override protected void onResume(){super.onResume();if(setupState!=null)refreshSetupState();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(i!=null&&i.getBooleanExtra("start_listening",false))startListening();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(48,28,48,48); scroll.addView(root);
        TextView title=new TextView(this); title.setText("Ei Chat TV"); title.setTextSize(34f); root.addView(title);
        TextView sub=new TextView(this); sub.setText("Configuração inicial"); sub.setTextSize(20f); sub.setPadding(0,2,0,12); root.addView(sub);
        setupState=new TextView(this); setupState.setTextSize(18f); setupState.setPadding(0,0,0,12); root.addView(setupState);
        status=new TextView(this); status.setTextSize(18f); status.setText("Preparando o Ei Chat TV…"); status.setPadding(0,0,0,12); root.addView(status);
        root.addView(button("1. Permitir microfone",v->requestMic()));
        root.addView(button("2. Continuar com ChatGPT",v->connectChatGpt()));
        root.addView(button("3. Definir Ei Chat como assistente padrão",v->openVoiceSettings()));
        root.addView(button("🎙 Testar comando de voz",v->startListening()));
        root.addView(button("🎮 Testar PlayStation 5",v->testPlayStation()));
        root.addView(button("Diagnóstico técnico",v->status.setText(Diagnostics.buildReport(this)+"\nChatGPT: "+chatGptAuth.accountLabel())));
        setContentView(scroll);
    }

    private Button button(String text,View.OnClickListener l){Button x=new Button(this);x.setText(text);x.setTextSize(19f);x.setAllCaps(false);x.setFocusable(true);x.setMinHeight(68);x.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,5,0,5);x.setLayoutParams(p);return x;}
    private void requestMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){status.setText("Microfone já está permitido.");refreshSetupState();}else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);}
    private void refreshSetupState(){
        boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        setupState.setText("Microfone: "+(mic?"✓ permitido":"✗ falta permitir")+"\nChatGPT: "+chatGptAuth.accountLabel()+"\nAssistente padrão: "+(Diagnostics.isDefaultAssistant(this)?"✓ Ei Chat":"✗ ainda não"));
        if(mic)status.setText("Microfone pronto. Agora conecte sua conta ChatGPT e depois defina o Ei Chat como assistente padrão.");
    }
    private void connectChatGpt(){status.setText("Abrindo o login oficial do ChatGPT…");chatGptAuth.signIn(new ChatGptAuth.Callback(){@Override public void onSuccess(String m){runOnUiThread(()->{refreshSetupState();status.setText(m+"\nAgora defina o Ei Chat como assistente padrão.");speak(m);});}@Override public void onError(String m){runOnUiThread(()->{status.setText(m);speak(m);});}});}
    private void initTts(){tts=new TextToSpeech(this,r->{if(r==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("pt","BR"));tts.setSpeechRate(1.02f);}});}
    private void initSpeechRecognizer(){if(!SpeechRecognizer.isRecognitionAvailable(this))return;recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(this);}
    private void startListening(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingListen=true;requestMic();return;}if(recognizer==null){status.setText("Reconhecimento de voz indisponível neste firmware.");return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);status.setText("Ouvindo… fale agora.");recognizer.startListening(i);}
    private void handleSpeech(String phrase){status.setText("Ouvi: “"+phrase+"”\nExecutando…");CommandEngine.Result r=commandEngine.execute(phrase);if(r.handled){status.setText(r.message);if(!r.success)speak(r.message);return;}status.setText("Consultando o ChatGPT…");aiClient.ask(phrase,new OpenAiClient.Callback(){@Override public void onSuccess(String t){runOnUiThread(()->{status.setText(t);speak(t);});}@Override public void onError(String m){runOnUiThread(()->{status.setText(m);speak(m);});}});}
    private void openVoiceSettings(){try{startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));}catch(Throwable t){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Throwable ignored){status.setText("A TCL não expôs a tela de seleção de assistente. Abra Configurações e procure Assistente digital.");}}}
    private void testPlayStation(){HdmiController.SwitchResult r=new HdmiController(this).switchToPlayStation();status.setText(r.message);if(!r.success)speak(r.message);}
    private void speak(String s){if(tts!=null&&s!=null&&!s.trim().isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"ei_chat_answer");}
    @Override public void onRequestPermissionsResult(int c,String[] p,int[] g){super.onRequestPermissionsResult(c,p,g);if(c==REQ_MIC){boolean ok=g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED;refreshSetupState();status.setText(ok?"Microfone liberado ✓\nPróxima etapa: Continuar com ChatGPT.":"O microfone é necessário para os comandos de voz.");if(ok&&pendingListen){pendingListen=false;startListening();}}}
    @Override public void onReadyForSpeech(Bundle p){status.setText("Ouvindo… fale agora.");}
    @Override public void onBeginningOfSpeech(){} @Override public void onRmsChanged(float r){} @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){status.setText("Entendendo…");}
    @Override public void onError(int e){status.setText("Falha no reconhecimento de voz (código "+e+"). Tente novamente.");}
    @Override public void onResults(Bundle b){ArrayList<String> m=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(m==null||m.isEmpty()){status.setText("Não entendi o comando.");return;}handleSpeech(m.get(0));}
    @Override public void onPartialResults(Bundle b){} @Override public void onEvent(int e,Bundle b){}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
