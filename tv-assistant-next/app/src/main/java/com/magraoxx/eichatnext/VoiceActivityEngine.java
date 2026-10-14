package com.magraoxx.eichatnext;

public interface VoiceActivityEngine extends AudioBus.Consumer {
    interface Listener {
        void onSpeechStarted();
        void onSpeechEnded(short[] utterancePcm, int sampleRate);
        void onError(String message);
    }

    void setListener(Listener listener);
    boolean initialize();
    void reset();
    void release();
}
