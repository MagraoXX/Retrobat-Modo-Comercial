package com.magraoxx.eichatnext;

public interface TextToSpeechEngine {
    interface Listener {
        void onStart();
        void onDone();
        void onError(String message);
    }

    void setListener(Listener listener);
    void speak(String text);
    void stop();
    boolean isSpeaking();
    void release();
}
