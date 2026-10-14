package com.magraoxx.eichatnext;

public interface WakeWordEngine {
    interface Listener {
        void onWakeWord(String phrase, float confidence);
        void onError(String message);
    }

    void setListener(Listener listener);
    void start();
    void stop();
    void acceptPcm(short[] samples, int count, int sampleRate);
    boolean isRunning();
}
