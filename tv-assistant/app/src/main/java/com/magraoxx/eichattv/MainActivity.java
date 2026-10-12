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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
 private static final int REQ_MIC=1001; private TextView status,setupState; private SpeechRecognizer recognizer; private TextToSpeech tts; private CommandEngine commandEngine; private OpenAiClient aiClient; private ChatGptAuth chatGptAuth; private boolean pendingListen;
 @Override protected void onCreate(Bundle b){super.onCreate(b);commandEngine=new CommandEngine(this);aiClient=new OpenAiClient(this);chatGptAuth=new ChatGptAuth(this);buildUi();initTts();initSpeechRecognizer();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){status.setText("Etapa 1 de 3 — permita o uso do microfone para continuar.");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);}else refreshSetupState();if(getIntent()!=null&&getIntent().getBooleanExtra("start_listening",false))startListening();}
 @Override protected void onResume(){super.onResume();if(setupState!=null)refreshSetupState();}
 private void buildUi(){ScrollView s=new ScrollView(this);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(48,24,48,48);s.addView(r);TextView t=new TextView(this);t.setText("Ei Chat TV");t.setTextSize(34);r.addView(t);TextView sub=new TextView(this);sub.setText("Configuração inicial");sub.setTextSize(20);r.addView(sub);setupState=new TextView(this);setupState.setTextSize(18);setupState.setPadding(0,10,0,10);r.addView(setupState);status=new TextView(this);status.setTextSize(18);status.setText("Preparando…");status.setPadding(0,0,0,10);r.addView(status);r.addView(button("1. Permitir microfone",v->requestMic()));r.addView(button("2. Continuar com ChatGPT",v->connectChatGpt()));r.addView(button("3. Definir Ei Chat como assistente padrão",v->openVoiceSettings()));r.addView(button("Testar comando de voz",v->startListening()));r.addView(button("Testar PlayStation 5",v->testPlayStation()));r.addView(button("Diagnóstico técnico",v->status.setText(Diagnostics.buildReport(this)+"\nChatGPT: "+chatGptAuth.accountLabel())));setContentView(s);}
 private Button button(String x,View.OnClickListener l){Button b=new Button(this);b.setText(x);b.setTextSize(19);b.setAllCaps(false);b.setFocusable(true);b.setMinHeight(68);b.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,5,0,5);b.setLayoutParams(p);return b;}
 private void requestMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){status.setText("Microfone já está permitido.");refreshSetupState();}else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);}
 private void refreshSetupState(){boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;setupState.setText("Microfone: "+(mic?"✓ permitido":"✗ falta permitir")+"\nChatGPT: "+chatGptAuth.accountLabel()+"\nAssistente padrão: verifique na etapa 3");if(mic)status.setText("Microfone pronto. Agora conecte sua conta ChatGPT e depois defina o Ei Chat como assistente padrão.");}
 private void connectChatGpt(){status.setText("Abrindo o login oficial do ChatGPT…");chatGptAuth.signIn(new ChatGptAuth.Callback(){public void onSuccess(String m){runOnUiThread(()->{refreshSetupState();status.setText(m+"\nAgora defina o Ei Chat como assistente padrão.");speak(m);});}public void onError(String m){runOnUiThread(()->{status.setText(m);speak(m);});}});}
 private void initTts(){tts=new TextToSpeech(this,r->{if(r==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("pt","BR"));tts.setSpeechRate(1.02f);}});}
 private void initSpeechRecognizer(){if(SpeechRecognizer.isRecognitionAvailable(this)){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(this);}}
 private void startListening(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingListen=true;requestMic();return;}if(recognizer==null){status.setText("Reconhecimento de voz indisponível neste firmware.");return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);status.setText("Ouvindo… fale agora.");recognizer.startListening(i);}
 private void handleSpeech(String q){CommandEngine.Result r=commandEngine.execute(q);if(r.handled){status.setText("Ouvi: “"+q+"”\n"+r.message);if(!r.success)speak(r.message);return;}status.setText("Consultando o ChatGPT…");aiClient.ask(q,new OpenAiClient.Callback(){public void onSuccess(String x){runOnUiThread(()->{status.setText(x);speak(x);});}public void onError(String x){runOnUiThread(()->{status.setText(x);speak(x);});}});}
 private void openVoiceSettings(){try{startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));}catch(Throwable t){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Throwable e){status.setText("Abra Configurações e procure Assistente digital.");}}}
 private void testPlayStation(){HdmiController.SwitchResult r=new HdmiController(this).switchToPlayStation();status.setText(r.message);if(!r.success)speak(r.message);}
 private void speak(String x){if(tts!=null&&x!=null&&!x.trim().isEmpty())tts.speak(x,TextToSpeech.QUEUE_FLUSH,null,"ei_chat_answer");}
 @Override public void onRequestPermissionsResult(int c,String[] p,int[] g){super.onRequestPermissionsResult(c,p,g);if(c==REQ_MIC){boolean ok=g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED;refreshSetupState();status.setText(ok?"Microfone liberado ✓\nPróxima etapa: Continuar com ChatGPT.":"O microfone é necessário para os comandos de voz.");if(ok&&pendingListen){pendingListen=false;startListening();}}}
 public void onReadyForSpeech(Bundle b){status.setText("Ouvindo… fale agora.");}public void onBeginningOfSpeech(){}public void onRmsChanged(float x){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){status.setText("Entendendo…");}public void onError(int e){status.setText("Falha no reconhecimento de voz (código "+e+").");}public void onResults(Bundle b){ArrayList<String> m=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(m==null||m.isEmpty()){status.setText("Não entendi.");return;}handleSpeech(m.get(0));}public void onPartialResults(Bundle b){}public void onEvent(int e,Bundle b){}
 @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
