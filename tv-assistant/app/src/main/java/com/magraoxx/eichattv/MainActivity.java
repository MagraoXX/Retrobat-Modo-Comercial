package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQ_MIC = 1001;

    private TextView status;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private CommandEngine commandEngine;
    private OpenAiClient aiClient;
    private boolean pendingListen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        commandEngine = new CommandEngine(this);
        aiClient = new OpenAiClient(this);
        buildUi();
        initTts();
        initSpeechRecognizer();

        boolean autoListen = getIntent() != null && getIntent().getBooleanExtra("start_listening", false);
        if (autoListen) startListening();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("start_listening", false)) startListening();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 36, 48, 48);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Ei Chat TV");
        title.setTextSize(34f);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Assistente experimental para TCL C6K / Android TV 14");
        subtitle.setTextSize(18f);
        subtitle.setPadding(0, 4, 0, 24);
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextSize(18f);
        status.setText(Diagnostics.buildReport(this));
        status.setTextIsSelectable(true);
        status.setPadding(0, 0, 0, 20);
        root.addView(status);

        root.addView(button("Ouvir comando agora", v -> startListening()));
        root.addView(button("Selecionar Ei Chat como assistente", v -> openVoiceSettings()));
        root.addView(button("Testar troca para o PlayStation 5", v -> testPlayStation()));
        root.addView(button("Atualizar diagnóstico", v -> status.setText(Diagnostics.buildReport(this))));
        root.addView(button("Conectar ChatGPT (em preparação)", v -> showAiSettings()));

        setContentView(scroll);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20f);
        b.setAllCaps(false);
        b.setMinHeight(72);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 8, 0, 8);
        b.setLayoutParams(p);
        return b;
    }

    private void initTts() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.02f);
            }
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingListen = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (recognizer == null) {
            status.setText("O serviço de reconhecimento de voz não está disponível neste firmware.");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        status.setText("Ouvindo…");
        recognizer.startListening(intent);
    }

    private void handleSpeech(String phrase) {
        status.setText("Ouvi: “" + phrase + "”\n\nExecutando…");
        CommandEngine.Result result = commandEngine.execute(phrase);
        if (result.handled) {
            status.setText("Ouvi: “" + phrase + "”\n\n" + result.message);
            if (!result.success) speak(result.message);
            return;
        }

        status.setText("Ouvi: “" + phrase + "”\n\nConsultando a IA…");
        aiClient.ask(phrase, new OpenAiClient.Callback() {
            @Override public void onSuccess(String text) {
                runOnUiThread(() -> {
                    status.setText(text);
                    speak(text);
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    status.setText(message);
                    speak(message);
                });
            }
        });
    }

    private void openVoiceSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
            startActivity(i);
        } catch (Throwable first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
                status.setText("A TCL não expôs a tela de seleção de assistente por Intent.");
            }
        }
    }

    private void testPlayStation() {
        HdmiController.SwitchResult result = new HdmiController(this).switchToPlayStation();
        status.setText(result.message + "\n\n" + Diagnostics.buildReport(this));
        if (!result.success) speak(result.message);
    }

    private void showAiSettings() {
        String hostId = getSharedPreferences("ei_chat_auth", MODE_PRIVATE)
                .getString("host_id", null);
        if (hostId == null) {
            hostId = "urn:uuid:" + java.util.UUID.randomUUID().toString();
            getSharedPreferences("ei_chat_auth", MODE_PRIVATE)
                    .edit().putString("host_id", hostId).apply();
        }
        new AlertDialog.Builder(this)
                .setTitle("Conectar ChatGPT")
                .setMessage("O Ei Chat TV usará uma conta ChatGPT autorizada por cada usuário. " +
                        "Esta instalação já tem uma identidade local própria. " +
                        "O login oficial ainda precisa ser adaptado e testado no navegador da Android TV. " +
                        "Nenhuma chave de API paga será solicitada nem utilizada automaticamente. " +
                        "Até a conexão ser concluída, os comandos locais da TV continuam disponíveis.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    private void speak(String text) {
        if (tts != null && text != null && !text.trim().isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ei_chat_answer");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && pendingListen) {
            pendingListen = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else status.setText("Sem permissão do microfone, o Ei Chat não pode ouvir comandos.");
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { status.setText("Ouvindo… fale agora."); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { status.setText("Entendendo…"); }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onError(int error) {
        status.setText("Falha no reconhecimento de voz (código " + error + "). Tente novamente.");
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            status.setText("Não entendi o comando.");
            return;
        }
        handleSpeech(matches.get(0));
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
