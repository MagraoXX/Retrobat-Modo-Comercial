package com.magraoxx.eichattv;

import android.service.voice.VoiceInteractionService;

public class EiChatVoiceInteractionService extends VoiceInteractionService {
    @Override
    public void onReady() {
        super.onReady();
        getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                .edit()
                .putLong("voice_service_ready_at", System.currentTimeMillis())
                .apply();
    }
}
