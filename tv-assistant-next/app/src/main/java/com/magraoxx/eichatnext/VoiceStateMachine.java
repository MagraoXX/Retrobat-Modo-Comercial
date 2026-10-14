package com.magraoxx.eichatnext;

public final class VoiceStateMachine {
    public enum State {
        IDLE("Em espera"),
        WAKE_DETECTED("Ativado"),
        LISTENING("Ouvindo"),
        THINKING("Pensando"),
        SPEAKING("Respondendo"),
        MUTED("Silenciado"),
        ERROR("Erro de voz");

        public final String label;
        State(String label) { this.label = label; }
    }

    public interface Listener {
        void onStateChanged(State state, String message);
    }

    private State state = State.IDLE;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public void reset() {
        transition(State.IDLE, "Diga “Ei Chat” quando quiser.");
    }

    public void simulateWake() {
        transition(State.WAKE_DETECTED, "Ei Chat detectado.");
        transition(State.LISTENING, "Pode falar normalmente.");
    }

    public void onListening(String partial) {
        transition(State.LISTENING, partial == null || partial.isBlank() ? "Estou ouvindo…" : partial);
    }

    public void onThinking(String transcript) {
        transition(State.THINKING, transcript == null ? "Entendendo o pedido…" : transcript);
    }

    public void onSpeaking(String response) {
        transition(State.SPEAKING, response == null ? "Respondendo…" : response);
    }

    public void onBargeIn() {
        transition(State.LISTENING, "Pode continuar, interrompi minha resposta.");
    }

    public void onMuted() {
        transition(State.MUTED, "Microfone silenciado.");
    }

    public void onError(String error) {
        transition(State.ERROR, error == null ? "Falha no motor de voz." : error);
    }

    private void transition(State next, String message) {
        state = next;
        if (listener != null) listener.onStateChanged(next, message);
    }
}
