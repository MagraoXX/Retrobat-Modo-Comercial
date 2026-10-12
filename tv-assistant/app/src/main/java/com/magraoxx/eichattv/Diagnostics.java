package com.magraoxx.eichattv;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.provider.Settings;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

public final class Diagnostics {
    private Diagnostics() {}

    public static String buildReport(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("Ei Chat TV — diagnóstico\n\n");

        String currentAssistant = Settings.Secure.getString(context.getContentResolver(), "voice_interaction_service");
        String expected = new ComponentName(context, EiChatVoiceInteractionService.class).flattenToString();
        boolean assistantActive = currentAssistant != null && currentAssistant.contains(context.getPackageName());
        out.append("Assistente padrão: ").append(assistantActive ? "SIM" : "NÃO").append("\n");
        out.append("Serviço esperado: ").append(expected).append("\n");

        boolean micPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        out.append("Permissão do microfone: ").append(micPermission ? "SIM" : "NÃO").append("\n");
        out.append("Reconhecimento de voz disponível: ")
                .append(SpeechRecognizer.isRecognitionAvailable(context) ? "SIM" : "NÃO").append("\n");

        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        List<String> inputs = new ArrayList<>();
        if (audio != null) {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                String name = device.getProductName() == null ? "microfone" : device.getProductName().toString();
                inputs.add(name + " [tipo " + device.getType() + "]");
            }
        }
        out.append("Entradas de áudio detectadas: ").append(inputs.isEmpty() ? "nenhuma" : inputs.size()).append("\n");
        for (String input : inputs) out.append("  • ").append(input).append("\n");

        AppCatalog catalog = new AppCatalog(context);
        out.append("Apps iniciáveis detectados: ").append(catalog.getApps().size()).append("\n");

        HdmiController hdmi = new HdmiController(context);
        List<HdmiController.InputInfo> tvInputs = hdmi.listInputs();
        int hdmiCount = 0;
        for (HdmiController.InputInfo input : tvInputs) if (input.hdmi) hdmiCount++;
        out.append("Entradas HDMI expostas: ").append(hdmiCount).append("\n");
        for (HdmiController.InputInfo input : tvInputs) {
            if (!input.hdmi) continue;
            out.append("  • ").append(input.label);
            if (input.logicalAddress != null) out.append(" (CEC ").append(input.logicalAddress).append(")");
            out.append("\n");
        }

        HdmiController.InputInfo ps = hdmi.findPlayStationInput();
        out.append("PlayStation identificado: ").append(ps == null ? "NÃO" : "SIM — " + ps.label).append("\n");
        out.append("\nObservação: detectar o microfone não prova que a hotword 'Ei Chat' pode ficar sempre ativa; isso só fica confirmado após selecionar este app como assistente e testar no firmware da TV.");
        return out.toString();
    }
}
