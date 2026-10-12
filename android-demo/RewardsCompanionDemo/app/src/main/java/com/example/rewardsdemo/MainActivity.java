package com.example.rewardsdemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Random;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final CheckBox[] checks = new CheckBox[4];
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    private Button simulateButton;
    private int earned = 0;
    private int step = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Rewards Companion");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("DEMO LOCAL — não controla Bing/Rewards");
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        TextView progressTitle = new TextView(this);
        progressTitle.setText("Progresso de exemplo");
        progressTitle.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(progressTitle);

        progressText = new TextView(this);
        progressText.setText("0 / 120 pontos");
        progressText.setPadding(0, dp(6), 0, dp(8));
        root.addView(progressText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(120);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));

        statusText = new TextView(this);
        statusText.setText("Pronto para demonstrar");
        statusText.setPadding(0, dp(8), 0, dp(18));
        root.addView(statusText);

        for (int i = 0; i < 4; i++) {
            CheckBox cb = new CheckBox(this);
            cb.setText("Atividade demonstrativa " + (i + 1) + "   +30");
            cb.setClickable(false);
            cb.setPadding(0, dp(6), 0, dp(6));
            checks[i] = cb;
            root.addView(cb);
        }

        simulateButton = new Button(this);
        simulateButton.setText("Simular fluxo local");
        simulateButton.setOnClickListener(v -> startSimulation());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(simulateButton, buttonParams);

        Button resetButton = new Button(this);
        resetButton.setText("Reiniciar demo");
        resetButton.setOnClickListener(v -> resetDemo());
        root.addView(resetButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Esta versão usa tarefas fictícias e atrasos aleatórios apenas dentro do próprio app. Não faz pesquisas, cliques externos, geração de pontos ou tentativa de evitar detecção.");
        note.setTextSize(12);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        setContentView(root);
    }

    private void startSimulation() {
        resetDemo();
        simulateButton.setEnabled(false);
        step = 0;
        statusText.setText("Iniciando demonstração...");
        runNextStep();
    }

    private void runNextStep() {
        if (step >= checks.length) {
            statusText.setText("Demonstração concluída — fluxo executado apenas dentro do app.");
            simulateButton.setEnabled(true);
            Toast.makeText(this, "Demo concluída", Toast.LENGTH_SHORT).show();
            return;
        }

        final int current = step;
        statusText.setText("Simulando etapa " + (current + 1) + " de " + checks.length + "...");
        long waitMs = 900 + random.nextInt(1301);
        handler.postDelayed(() -> {
            checks[current].setChecked(true);
            earned += 30;
            progressBar.setProgress(earned);
            progressText.setText(earned + " / 120 pontos");
            step++;
            runNextStep();
        }, waitMs);
    }

    private void resetDemo() {
        handler.removeCallbacksAndMessages(null);
        earned = 0;
        step = 0;
        for (CheckBox cb : checks) {
            if (cb != null) cb.setChecked(false);
        }
        if (progressBar != null) progressBar.setProgress(0);
        if (progressText != null) progressText.setText("0 / 120 pontos");
        if (statusText != null) statusText.setText("Pronto para demonstrar");
        if (simulateButton != null) simulateButton.setEnabled(true);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
