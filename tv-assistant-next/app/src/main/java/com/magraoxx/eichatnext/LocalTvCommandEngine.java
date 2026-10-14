package com.magraoxx.eichatnext;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalTvCommandEngine {
    public static final class Result {
        public final boolean handled;
        public final boolean success;
        public final String message;

        private Result(boolean handled, boolean success, String message) {
            this.handled = handled;
            this.success = success;
            this.message = message;
        }

        public static Result unhandled() { return new Result(false, false, ""); }
        public static Result ok(String message) { return new Result(true, true, message); }
        public static Result fail(String message) { return new Result(true, false, message); }
    }

    private static final class AppEntry {
        final String label;
        final Intent launchIntent;
        AppEntry(String label, Intent launchIntent) {
            this.label = label;
            this.launchIntent = launchIntent;
        }
    }

    private final Context context;
    private final AudioManager audio;

    public LocalTvCommandEngine(Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public Result execute(String rawText) {
        String text = normalize(rawText).replaceFirst("^(ei chat|hey chat|chat)\\s+", "").trim();
        if (text.isEmpty()) return Result.unhandled();

        if (containsAny(text, "que horas sao", "que hora e", "qual e a hora", "me diz a hora")) {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", new Locale("pt", "BR"));
            return Result.ok("Agora são " + f.format(new Date()) + ".");
        }
        if (containsAny(text, "que dia e hoje", "qual a data de hoje", "data de hoje")) {
            SimpleDateFormat f = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR"));
            return Result.ok("Hoje é " + f.format(new Date()) + ".");
        }

        Result volume = handleVolume(text);
        if (volume.handled) return volume;

        if (containsAny(text, "ativar o mudo", "ativa o mudo", "silenciar", "sem som", "mute")) {
            if (audio == null) return Result.fail("Controle de áudio indisponível.");
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            return Result.ok("Som silenciado.");
        }
        if (containsAny(text, "tirar do mudo", "desativar o mudo", "desmuta", "voltar som", "unmute")) {
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

        Result channel = handleChannel(text);
        if (channel.handled) return channel;

        String install = extractAfterPrefix(text, "instala ", "instalar ", "instale ", "baixar app ", "baixa app ");
        if (install != null) return openStore(install);

        if (text.equals("home") || containsAny(text, "tela inicial", "ir para o inicio", "vai para o inicio", "fechar aplicativo", "sair do aplicativo")) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(home);
                return Result.ok("Tela inicial.");
            } catch (Throwable error) {
                return Result.fail("Não consegui abrir a tela inicial.");
            }
        }

        String app = extractAfterPrefix(text,
                "abre ", "abrir ", "abra ", "entra no ", "entra na ", "entrar no ", "entrar na ", "inicia ", "iniciar ");
        if (app != null) {
            Result opened = openApp(app);
            if (opened.handled) return opened;
        }

        return Result.unhandled();
    }

    private Result handleVolume(String text) {
        if (!containsAny(text, "volume", "som")) return Result.unhandled();
        if (audio == null) return Result.fail("Controle de volume indisponível.");

        int number = extractNumber(text, 10);
        boolean lower = containsAny(text, "abaixa", "abaixar", "baixa", "diminuir", "diminui", "menos");
        boolean raise = containsAny(text, "aumenta", "aumentar", "sobe", "subir", "mais");
        boolean exact = text.matches(".*\\b(para|pra|em)\\s+(o\\s+)?(volume\\s+)?\\d{1,3}\\b.*")
                || text.matches(".*\\b(volume|som)\\s+(em\\s+)?\\d{1,3}\\b.*");

        if (exact) {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int percent = Math.max(0, Math.min(100, number));
            int target = Math.round(max * (percent / 100f));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            return Result.ok("Volume em " + percent + "%.");
        }
        if (lower || raise) {
            int amount = Math.max(1, Math.min(25, number));
            int direction = lower ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE;
            for (int i = 0; i < amount; i++) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, i == amount - 1 ? AudioManager.FLAG_SHOW_UI : 0);
            }
            return Result.ok(lower ? "Volume reduzido." : "Volume aumentado.");
        }
        return Result.unhandled();
    }

    private Result media(int keyCode, String message) {
        if (audio == null) return Result.fail("Controle de mídia indisponível.");
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        return Result.ok(message);
    }

    private Result openApp(String spoken) {
        String target = normalize(spoken)
                .replaceFirst("^(o|a)\\s+", "")
                .replace("amazon prime", "prime video")
                .replace("disney plus", "disney")
                .trim();
        if (target.isEmpty()) return Result.unhandled();

        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> unique = new LinkedHashMap<>();
        collectApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, unique);
        collectApps(pm, Intent.CATEGORY_LAUNCHER, unique);

        AppEntry best = null;
        double score = 0;
        for (AppEntry entry : unique.values()) {
            String name = normalize(entry.label);
            double current;
            if (name.equals(target)) current = 1.0;
            else if (name.contains(target) || target.contains(name)) current = 0.92;
            else current = similarity(name, target);
            if (current > score) {
                score = current;
                best = entry;
            }
        }
        if (best == null || score < 0.58) return Result.fail("Não encontrei o aplicativo " + spoken + ".");
        try {
            context.startActivity(best.launchIntent);
            return Result.ok("Abrindo " + best.label + ".");
        } catch (Throwable error) {
            return Result.fail("Não consegui abrir " + best.label + ".");
        }
    }

    private void collectApps(PackageManager pm, String category, Map<String, AppEntry> out) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(query, PackageManager.MATCH_ALL)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())) continue;
            CharSequence cs = info.loadLabel(pm);
            String label = cs == null ? pkg : cs.toString();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.put(pkg, new AppEntry(label, launch));
            }
        }
    }

    private Result openStore(String name) {
        try {
            String q = URLEncoder.encode(name, StandardCharsets.UTF_8.name());
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + q + "&c=apps"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(market);
            return Result.ok("Procurando " + name + " na Play Store.");
        } catch (Throwable first) {
            try {
                String q = URLEncoder.encode(name, StandardCharsets.UTF_8.name());
                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + q + "&c=apps"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(web);
                return Result.ok("Procurando " + name + " na Play Store.");
            } catch (Throwable ignored) {
                return Result.fail("Não consegui abrir a Play Store.");
            }
        }
    }

    private Result handleChannel(String text) {
        if (containsAny(text, "canal acima", "canal para cima", "sobe canal", "proximo canal")) {
            return media(KeyEvent.KEYCODE_CHANNEL_UP, "Canal acima.");
        }
        if (containsAny(text, "canal abaixo", "canal para baixo", "desce canal", "canal anterior")) {
            return media(KeyEvent.KEYCODE_CHANNEL_DOWN, "Canal abaixo.");
        }
        Matcher m = Pattern.compile("(?:^|\\s)canal\\s+(\\d{1,3})(?:[ .-](\\d{1,2}))?$").matcher(text);
        if (m.find()) {
            String requested = m.group(1) + (m.group(2) == null ? "" : "." + m.group(2));
            Result tuned = tuneChannel(requested);
            if (tuned.handled) return tuned;
        }
        if (text.equals("tv") || text.equals("televisao") || text.equals("tv ao vivo") || containsAny(text, "abre tv", "abrir tv", "modo tv")) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
                return Result.ok("Abrindo TV ao vivo.");
            } catch (Throwable error) {
                return Result.fail("A TCL não expôs a TV ao vivo para este aplicativo.");
            }
        }
        return Result.unhandled();
    }

    private Result tuneChannel(String requested) {
        String[] projection = { TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER };
        try (Cursor cursor = context.getContentResolver().query(TvContract.Channels.CONTENT_URI, projection, null, null, null)) {
            if (cursor == null) return Result.unhandled();
            int idCol = cursor.getColumnIndexOrThrow(TvContract.Channels._ID);
            int numberCol = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
            while (cursor.moveToNext()) {
                String number = cursor.getString(numberCol);
                if (number != null && normalizeChannel(number).equals(normalizeChannel(requested))) {
                    Uri uri = TvContract.buildChannelUri(cursor.getLong(idCol));
                    context.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return Result.ok("Canal " + requested + ".");
                }
            }
            return Result.fail("Não encontrei o canal " + requested + ".");
        } catch (Throwable error) {
            return Result.unhandled();
        }
    }

    private static String normalizeChannel(String value) {
        return value == null ? "" : value.trim().replace(',', '.').replaceAll("\\s+", "");
    }

    private static String extractAfterPrefix(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                String value = text.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static int extractNumber(String text, int fallback) {
        Matcher matcher = Pattern.compile("\\b(\\d{1,3})\\b").matcher(text);
        if (!matcher.find()) return fallback;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('+', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev; prev = curr; curr = temp;
        }
        return 1.0 - (prev[b.length()] / (double) Math.max(a.length(), b.length()));
    }
}
