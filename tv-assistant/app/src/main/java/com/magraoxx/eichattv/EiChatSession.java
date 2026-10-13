package com.magraoxx.eichattv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class EiChatSession extends android.service.voice.VoiceInteractionSession {
    private TextView title,status; private SpeechRecognizer speech; private TextToSpeech tts;
    private CommandEngine engine; private OpenAiClient ai; private ChatGptAuth auth;
    public EiChatSession(Context c){super(c);engine=new CommandEngine(c);ai=new OpenAiClient(c);auth=new ChatGptAuth(c);}
    private GradientDrawable bg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(24,27,34));g.setCornerRadius(30);g.setStroke(3,Color.rgb(75,160,255));return g;}
    private TextView tv(String s,float size){TextView v=new TextView(getContext());v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);v.setPadding(20,8,20,8);return v;}
    @Override public View onCreateContentView(){LinearLayout box=new LinearLayout(getContext());box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(28,22,28,22);box.setBackground(bg());title=tv("Ei Chat",24);title.setTypeface(null,1);status=tv("Pronto",18);box.addView(title,new LinearLayout.LayoutParams(-1,-2));box.addView(status,new LinearLayout.LayoutParams(-1,-2));return box;}
    @Override public void onShow(Bundle args,int flags){super.onShow(args,flags);if(title==null)setContentView(onCreateContentView());startListening();}
    private void set(String s){if(status!=null)status.setText(s);}
    private void startListening(){
        if(!auth.isConnected()){set("Abra o Ei Chat e conecte sua conta ChatGPT primeiro.");speak("Abra o Ei Chat e conecte sua conta ChatGPT primeiro.");return;}
        if(getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){set("Permissão de microfone necessária.");return;}
        if(!SpeechRecognizer.isRecognitionAvailable(getContext())){set("Reconhecimento de voz indisponível nesta TV.");return;}
        destroySpeech(); speech=SpeechRecognizer.createSpeechRecognizer(getContext());
        speech.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){set("Ouvindo…");} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){set("Entendendo…");}
            public void onError(int e){set("Não consegui ouvir. Tente novamente.");}
            public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a==null||a.isEmpty()){set("Não entendi.");return;}handle(a.get(0));}
            public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
        });
        android.content.Intent i=new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);try{speech.startListening(i);}catch(Throwable e){set("Falha ao abrir o microfone.");}
    }
    private void handle(String q){set("“"+q+"”");CommandEngine.Result r=engine.execute(q);if(r.handled){set(r.message);speak(r.message);return;}set("Pensando…");ai.ask(q,new OpenAiClient.Callback(){public void onSuccess(String x){getContext().getMainExecutor().execute(()->{set(x);speak(x);});}public void onError(String x){getContext().getMainExecutor().execute(()->set(x));}});}
    private void speak(String s){if(tts==null){tts=new TextToSpeech(getContext(),x->{if(x==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("pt","BR"));tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"ei_chat_session");}});}else tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"ei_chat_session");}
    private void destroySpeech(){if(speech!=null){try{speech.cancel();}catch(Throwable ignored){}try{speech.destroy();}catch(Throwable ignored){}speech=null;}}
    @Override public void onHide(){destroySpeech();super.onHide();}
    @Override public void onDestroy(){destroySpeech();if(tts!=null)tts.shutdown();super.onDestroy();}
}
