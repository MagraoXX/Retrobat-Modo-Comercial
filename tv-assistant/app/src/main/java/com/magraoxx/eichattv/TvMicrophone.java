package com.magraoxx.eichattv;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

public final class TvMicrophone {
    private TvMicrophone() {}

    public static AudioDeviceInfo findBuiltIn(Context context) {
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(am==null)return null;
        for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS))
            if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)return d;
        return null;
    }

    public static String report(Context context) {
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(am==null)return "Áudio indisponível";
        StringBuilder b=new StringBuilder("Entradas de áudio detectadas:");
        AudioDeviceInfo[] devices=am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        if(devices.length==0)return b.append(" nenhuma").toString();
        for(AudioDeviceInfo d:devices){
            b.append("\n• ").append(typeName(d.getType()));
            CharSequence n=d.getProductName();
            if(n!=null&&n.length()>0)b.append(" — ").append(n);
            b.append(" [id ").append(d.getId()).append(']');
        }
        AudioDeviceInfo tv=findBuiltIn(context);
        b.append("\nPreferência Ei Chat: ").append(tv==null?"microfone interno não exposto pela TCL":"MIC INTERNO DA TV — "+tv.getProductName());
        return b.toString();
    }

    private static String typeName(int t){
        switch(t){
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:return "MIC INTERNO DA TV";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:return "MIC BLUETOOTH/CONTROLE";
            case AudioDeviceInfo.TYPE_USB_DEVICE:return "MIC USB";
            case AudioDeviceInfo.TYPE_USB_HEADSET:return "HEADSET USB";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:return "HEADSET COM FIO";
            default:return "ENTRADA tipo "+t;
        }
    }
}
