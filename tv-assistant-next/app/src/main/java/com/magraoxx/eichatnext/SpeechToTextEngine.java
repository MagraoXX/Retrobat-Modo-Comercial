package com.magraoxx.eichatnext;

public interface SpeechToTextEngine {
    interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }

    void setListener(Listener listener);
    void startUtterance();
    void acceptPcm(short[] samples, int count, int sampleRate);
    void finishUtterance();
    void reset();
    void release();
}
