package com.magraoxx.eichattv;

import android.content.Intent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * RecognitionService exigido pelo framework para aceitar o Ei Chat como
 * VoiceInteractionService. No Android 12+ o atributo recognitionService do
 * assistente não é usado para a captura principal, mas continua obrigatório.
 * A sessão do Ei Chat usa o SpeechRecognizer do sistema (Google/TCL).
 */
public final class EiChatRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (Throwable ignored) {
        }
    }
}
