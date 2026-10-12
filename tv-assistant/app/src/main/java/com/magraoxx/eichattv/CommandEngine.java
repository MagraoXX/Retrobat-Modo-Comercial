package com.magraoxx.eichattv;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandEngine {
    public static final class Result {
        public final boolean handled;
        public final boolean success;
        public final String message;

        Result(boolean handled, boolean success, String message) {
            this.handled = handled;
            this.success = success;
            this.message = message;
        }

        static Result unhandled() { return new Result(false, false, ""); }
        static Result ok(String msg) { return new Result(true, true, msg); }
        static Result fail(String msg) { return new Result(true, false, msg); }
    }

    private final Context context;
    private final AudioManager audio;
    private final AppCatalog apps;
    private final HdmiController hdmi;
    private final TvChannelController tv;
    private final StoreController store;

    public CommandEngine(Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.apps = new AppCatalog(context);
        this.hdmi = new HdmiController(context);
        this.tv = new TvChannelController(context);
        this.store = new StoreController(context);
    }

    public Result execute(String rawText) {
        String text = AppCatalog.normalize(rawText);
        text = text.replaceFirst("^(ei chat|hey chat|chat)\\s+", "").trim();
        if (text.isEmpty()) return Result.unhandled();

        Result volume = handleVolume(text);
        if (volume.handled) return volume;

        if (containsAny(text, "mudo", "silenciar", "sem som", "mute")) {
            if (audio == null) return Result.fail("Controle de áudio indisponível.");
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            return Result.ok("Som silenciado.");
        }
        if (containsAny(text, "tirar do mudo", "desmutar", "voltar som", "unmute")) {
            if (audio == null) return Result.fail("Controle de áudio indisponível.");
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
            return Result.ok("Som restaurado.");
        }

        if (containsAny(text, "pausa", "pausar", "pause")) return media(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pausado.");
        if (containsAny(text, "continua", "continuar", "reproduzir", "play")) return media(KeyEvent.KEYCODE_MEDIA_PLAY, "Continuando.");
        if (containsAny(text, "proximo", "proxima musica", "proximo video")) return media(KeyEvent.KEYCODE_MEDIA_NEXT, "Próximo.");
        if (containsAny(text, "anterior", "volta musica", "video anterior")) return media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Anterior.");
        if (containsAny(text, "avanca", "avancar")) return media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Avançando.");
        if (containsAny(text, "retrocede", "retroceder")) return media(KeyEvent.KEYCODE_MEDIA_REWIND, "Retrocedendo.");

        Result tvResult = handleTv(text);
        if (tvResult.handled) return tvResult;

        String installName = extractInstallName(text);
        if (installName != null) {
            boolean opened = store.search(installName);
            return opened
                    ? Result.ok("Abrindo " + installName + " na Play Store.")
                    : Result.fail("Não consegui abrir a Play Store para procurar " + installName + ".");
        }

        if (text.equals("home") || containsAny(text, "inicio", "tela inicial", "fechar aplicativo", "fecha aplicativo", "sair do aplicativo", "sai do aplicativo")) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);
            return Result.ok("Tela inicial.");
        }

        if (containsAny(text, "playstation 5", "playstation", "ps5", "video game", "videogame")) {
            if (startsWithAny(text, "abre", "abrir", "abra", "liga", "ligar", "vai para", "muda para", "troca para")) {
                HdmiController.SwitchResult switched = hdmi.switchToPlayStation();
                return switched.success ? Result.ok(switched.message) : Result.fail(switched.message);
            }
        }

        String appName = extractAppName(text);
        if (appName != null) {
            apps.refresh();
            AppCatalog.AppEntry app = apps.findBest(appName);
            if (app == null) return Result.fail("Não encontrei o aplicativo " + appName + ".");
            context.startActivity(app.launchIntent);
            return Result.ok("Abrindo " + app.label + ".");
        }

        return Result.unhandled();
    }

    private Result handleTv(String text) {
        if (containsAny(text, "canal acima", "canal pra cima", "canal para cima", "sobe canal", "subir canal", "proximo canal")) {
            TvChannelController.Result r = tv.step(1);
            return r.success ? Result.ok(r.message) : Result.fail(r.message);
        }
        if (containsAny(text, "canal abaixo", "canal pra baixo", "canal para baixo", "desce canal", "descer canal", "canal anterior")) {
            TvChannelController.Result r = tv.step(-1);
            return r.success ? Result.ok(r.message) : Result.fail(r.message);
        }

        Matcher channel = Pattern.compile("(?:^|\\s)canal\\s+(\\d{1,3})(?:\\s+(\\d{1,2}))?$").matcher(text);
        if (channel.find()) {
            String requested = channel.group(1);
            if (channel.group(2) != null) requested += "." + channel.group(2);
            TvChannelController.Result r = tv.tuneByNumber(requested);
            return r.success ? Result.ok(r.message) : Result.fail(r.message);
        }

        if (text.equals("tv") || text.equals("televisao") || text.equals("tv ao vivo") ||
                startsWithAny(text, "vai pra tv", "vai para tv", "abre tv", "abrir tv", "modo tv", "coloca tv")) {
            TvChannelController.Result r = tv.openLiveTv();
            return r.success ? Result.ok(r.message) : Result.fail(r.message);
        }
        return Result.unhandled();
    }

    private Result handleVolume(String text) {
        if (!containsAny(text, "volume", "som")) return Result.unhandled();
        if (audio == null) return Result.fail("Controle de volume indisponível.");

        int number = extractNumber(text, 1);
        if (containsAny(text, "abaixa", "abaixar", "baixa", "diminuir", "diminui", "menos")) {
            for (int i = 0; i < Math.max(1, number); i++) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                        i == Math.max(1, number) - 1 ? AudioManager.FLAG_SHOW_UI : 0);
            }
            return Result.ok("Volume reduzido em " + Math.max(1, number) + ".");
        }
        if (containsAny(text, "aumenta", "aumentar", "sobe", "subir", "mais")) {
            for (int i = 0; i < Math.max(1, number); i++) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                        i == Math.max(1, number) - 1 ? AudioManager.FLAG_SHOW_UI : 0);
            }
            return Result.ok("Volume aumentado em " + Math.max(1, number) + ".");
        }
        if (text.matches(".*\\b(volume|som)\\s+(em\\s+)?\\d{1,3}\\b.*")) {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int percent = Math.max(0, Math.min(100, number));
            int target = Math.round(max * (percent / 100f));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            return Result.ok("Volume em " + percent + "%.");
        }
        return Result.unhandled();
    }

    private Result media(int keyCode, String message) {
        if (audio == null) return Result.fail("Controle de mídia indisponível.");
        long now = android.os.SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        return Result.ok(message);
    }

    private String extractInstallName(String text) {
        String[] prefixes = {
                "instala ", "instalar ", "instale ", "baixa app ", "baixar app ",
                "baixa aplicativo ", "baixar aplicativo "
        };
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                String value = text.substring(prefix.length()).trim();
                value = value.replaceFirst("^(o|a)?\\s*(app|aplicativo)?\\s*", "").trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private String extractAppName(String text) {
        String[] prefixes = {
                "abre ", "abrir ", "abra ", "entra no ", "entra na ", "entrar no ", "entrar na ",
                "inicia ", "iniciar ", "coloca o ", "coloca a "
        };
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                String value = text.substring(prefix.length()).trim();
                value = value.replaceFirst("^(app|aplicativo)\\s+(do|da|de)?\\s*", "");
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private static int extractNumber(String text, int fallback) {
        Matcher m = Pattern.compile("\\b(\\d{1,3})\\b").matcher(text);
        if (!m.find()) return fallback;
        try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    private static boolean startsWithAny(String text, String... prefixes) {
        for (String p : prefixes) if (text.startsWith(p)) return true;
        return false;
    }
}
