package com.magraoxx.eichattv;

import android.content.Context;

public final class OpenAiClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    public OpenAiClient(Context context) {
    }

    public void ask(String prompt, Callback callback) {
        callback.onError("A parte inteligente está em modo gratuito somente. Nenhuma API paga será chamada. Configure um provedor gratuito compatível nas próximas versões.");
    }
}
