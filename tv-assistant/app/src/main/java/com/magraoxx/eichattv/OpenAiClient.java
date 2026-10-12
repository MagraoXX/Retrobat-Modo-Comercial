package com.magraoxx.eichattv;

import android.content.Context;

public final class OpenAiClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private final ChatGptAuth auth;

    public OpenAiClient(Context context) {
        auth = new ChatGptAuth(context);
    }

    public void ask(String prompt, Callback callback) {
        if (!auth.isConnected()) {
            callback.onError("Conecte sua conta ChatGPT no Ei Chat TV primeiro.");
            return;
        }
        callback.onError("Conta ChatGPT conectada. A chamada de IA será habilitada depois do teste do fluxo de login nesta TV.");
    }
}
