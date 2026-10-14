package com.magraoxx.eichatnext;

public final class VoiceSessionController {
    public interface Listener {
        void onActivated(String phrase);
        void onCommand(String command);
        void onEnded();
        void onIgnored(String transcript);
    }

    private final WakePhraseMatcher matcher = new WakePhraseMatcher();
    private final Listener listener;
    private boolean active;

    public VoiceSessionController(Listener listener) {
        this.listener = listener;
    }

    public synchronized boolean isActive() { return active; }

    public synchronized void activateManually() {
        if (active) return;
        active = true;
        if (listener != null) listener.onActivated("botão");
    }

    public synchronized void end() {
        if (!active) return;
        active = false;
        if (listener != null) listener.onEnded();
    }

    public synchronized void onTranscript(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) return;

        if (active) {
            if (matcher.isEndPhrase(transcript)) {
                active = false;
                if (listener != null) listener.onEnded();
                return;
            }

            WakePhraseMatcher.Match wake = matcher.match(transcript);
            String command = wake.matched && !wake.command.isEmpty() ? wake.command : transcript.trim();
            if (listener != null) listener.onCommand(command);
            return;
        }

        WakePhraseMatcher.Match wake = matcher.match(transcript);
        if (!wake.matched) {
            if (listener != null) listener.onIgnored(transcript);
            return;
        }

        active = true;
        if (listener != null) listener.onActivated(wake.phrase);
        if (!wake.command.isEmpty() && listener != null) listener.onCommand(wake.command);
    }
}
