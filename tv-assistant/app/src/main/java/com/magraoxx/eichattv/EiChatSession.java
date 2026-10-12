package com.magraoxx.eichattv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

public class EiChatSession extends VoiceInteractionSession {
    public EiChatSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Intent intent = new Intent(getContext(), MainActivity.class)
                .putExtra("start_listening", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startVoiceActivity(intent);
        } catch (Throwable t) {
            try {
                getContext().startActivity(intent);
            } catch (Throwable ignored) {
            }
        }
    }
}
