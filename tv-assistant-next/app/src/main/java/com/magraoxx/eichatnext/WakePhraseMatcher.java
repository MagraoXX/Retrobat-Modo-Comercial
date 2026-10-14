package com.magraoxx.eichatnext;

import java.text.Normalizer;
import java.util.Locale;

public final class WakePhraseMatcher {
    public static final class Match {
        public final boolean matched;
        public final String phrase;
        public final String command;

        private Match(boolean matched, String phrase, String command) {
            this.matched = matched;
            this.phrase = phrase;
            this.command = command;
        }

        public static Match none() { return new Match(false, "", ""); }
    }

    private static final String[] PHRASES = {"ei chat", "hey chat", "chat"};

    public Match match(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) return Match.none();
        String normalized = normalize(transcript);

        for (String phrase : PHRASES) {
            if (normalized.equals(phrase)) return new Match(true, phrase, "");
            if (normalized.startsWith(phrase + " ")) {
                String command = normalized.substring(phrase.length()).trim();
                return new Match(true, phrase, command);
            }
        }
        return Match.none();
    }

    public boolean isEndPhrase(String transcript) {
        String n = normalize(transcript);
        return n.equals("encerrar") || n.contains("encerrar chamada") || n.contains("finalizar chamada");
    }

    private static String normalize(String value) {
        String text = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.startsWith("ei, chat")) text = text.replaceFirst("ei, chat", "ei chat");
        return text;
    }
}
