package com.magraoxx.eichatnext;

public interface AssistantRouter {
    interface Callback {
        void onAnswer(String answer);
        void onError(String message);
    }

    void ask(String text, ScreenContextProvider.Context screenContext, Callback callback);
    void cancel();
}
