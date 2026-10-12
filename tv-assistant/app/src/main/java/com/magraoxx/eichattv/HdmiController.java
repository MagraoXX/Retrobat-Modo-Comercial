package com.magraoxx.eichattv;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class HdmiController {
    public static final class InputInfo {
        public final String id;
        public final String label;
        public final boolean hdmi;
        public final Integer logicalAddress;

        InputInfo(String id, String label, boolean hdmi, Integer logicalAddress) {
            this.id = id;
            this.label = label;
            this.hdmi = hdmi;
            this.logicalAddress = logicalAddress;
        }
    }

    public static final class SwitchResult {
        public final boolean success;
        public final String message;

        SwitchResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final Context context;

    public HdmiController(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<InputInfo> listInputs() {
        List<InputInfo> result = new ArrayList<>();
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return result;
        for (TvInputInfo info : manager.getTvInputList()) {
            String label;
            try {
                CharSequence cs = info.loadLabel(context);
                label = cs == null ? info.getId() : cs.toString();
            } catch (Throwable t) {
                label = info.getId();
            }
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            Integer logical = extractLogicalAddress(info);
            result.add(new InputInfo(info.getId(), label, hdmi, logical));
        }
        return result;
    }

    public InputInfo findPlayStationInput() {
        InputInfo fallback = null;
        for (InputInfo input : listInputs()) {
            if (!input.hdmi) continue;
            String n = AppCatalog.normalize(input.label + " " + input.id);
            if (n.contains("playstation") || n.contains("ps5") || n.contains("sony interactive")) {
                return input;
            }
            if (fallback == null && input.logicalAddress != null) fallback = input;
        }
        return findCecPlayStation(fallback);
    }

    private InputInfo findCecPlayStation(InputInfo fallback) {
        try {
            Object manager = context.getSystemService("hdmi_control");
            if (manager == null) return fallback;
            Method getTvClient = manager.getClass().getMethod("getTvClient");
            Object client = getTvClient.invoke(manager);
            if (client == null) return fallback;
            Method getDeviceList = client.getClass().getMethod("getDeviceList");
            Object listObj = getDeviceList.invoke(client);
            if (!(listObj instanceof List)) return fallback;
            for (Object device : (List<?>) listObj) {
                Method getName = device.getClass().getMethod("getDisplayName");
                Method getLogical = device.getClass().getMethod("getLogicalAddress");
                String name = String.valueOf(getName.invoke(device));
                int logical = ((Number) getLogical.invoke(device)).intValue();
                String n = AppCatalog.normalize(name);
                if (n.contains("playstation") || n.contains("ps5") || n.contains("sony")) {
                    return new InputInfo("cec:" + logical, name, true, logical);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public SwitchResult switchToPlayStation() {
        InputInfo target = findPlayStationInput();
        if (target == null) {
            return new SwitchResult(false, "Nenhum PlayStation foi identificado por HDMI-CEC ainda.");
        }
        if (target.logicalAddress == null) {
            return new SwitchResult(false, "PlayStation encontrado em " + target.label + ", mas a TCL não expôs o endereço CEC para troca direta.");
        }
        try {
            Object manager = context.getSystemService("hdmi_control");
            if (manager == null) return new SwitchResult(false, "Serviço HDMI-CEC não exposto pelo firmware.");
            Object client = manager.getClass().getMethod("getTvClient").invoke(manager);
            if (client == null) return new SwitchResult(false, "Cliente HDMI-CEC de TV não disponível.");

            Method select = null;
            for (Method m : client.getClass().getMethods()) {
                if (m.getName().equals("deviceSelect") && m.getParameterTypes().length == 2) {
                    select = m;
                    break;
                }
            }
            if (select == null) return new SwitchResult(false, "O firmware não expôs deviceSelect para apps comuns.");

            Class<?> callbackType = select.getParameterTypes()[1];
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType},
                    (proxy, method, args) -> null);
            select.invoke(client, target.logicalAddress, callback);
            return new SwitchResult(true, "Trocando para " + target.label + ".");
        } catch (SecurityException se) {
            return new SwitchResult(false, "A TCL bloqueou a permissão HDMI-CEC para este app.");
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            if (cause instanceof SecurityException) {
                return new SwitchResult(false, "A TCL bloqueou a permissão HDMI-CEC para este app.");
            }
            return new SwitchResult(false, "Falha ao trocar HDMI: " + cause.getClass().getSimpleName());
        }
    }

    private Integer extractLogicalAddress(TvInputInfo info) {
        if (Build.VERSION.SDK_INT < 24) return null;
        try {
            Object hdmi = info.getClass().getMethod("getHdmiDeviceInfo").invoke(info);
            if (hdmi == null) return null;
            Object value = hdmi.getClass().getMethod("getLogicalAddress").invoke(hdmi);
            return value instanceof Number ? ((Number) value).intValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
