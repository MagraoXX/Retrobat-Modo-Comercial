package com.magraoxx.eichatnext;

import java.util.concurrent.CopyOnWriteArrayList;

public final class AudioBus {
    public interface Consumer {
        void onPcm(short[] samples, int count, int sampleRate, float rmsDb);
    }

    private final CopyOnWriteArrayList<Consumer> consumers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer consumer) {
        if (consumer != null && !consumers.contains(consumer)) consumers.add(consumer);
    }

    public void unsubscribe(Consumer consumer) {
        if (consumer != null) consumers.remove(consumer);
    }

    public void publish(short[] samples, int count, int sampleRate, float rmsDb) {
        for (Consumer consumer : consumers) {
            try {
                consumer.onPcm(samples, count, sampleRate, rmsDb);
            } catch (Throwable ignored) {
                // One module must never break audio delivery to the others.
            }
        }
    }

    public void clear() {
        consumers.clear();
    }
}
