package com.magraoxx.eichattv;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TvChannelController {
    public static final class Channel {
        final long id;
        final String number;
        final String name;

        Channel(long id, String number, String name) {
            this.id = id;
            this.number = number == null ? "" : number.trim();
            this.name = name == null ? "" : name.trim();
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final Context context;

    public TvChannelController(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public List<Channel> listChannels() {
        if (!hasPermission()) return Collections.emptyList();
        List<Channel> out = new ArrayList<>();
        String[] projection = new String[] {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_BROWSABLE
        };
        try (Cursor c = context.getContentResolver().query(
                TvContract.Channels.CONTENT_URI, projection, null, null, null)) {
            if (c == null) return out;
            int idCol = c.getColumnIndexOrThrow(TvContract.Channels._ID);
            int numCol = c.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
            int nameCol = c.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NAME);
            int browsableCol = c.getColumnIndexOrThrow(TvContract.Channels.COLUMN_BROWSABLE);
            while (c.moveToNext()) {
                if (!c.isNull(browsableCol) && c.getInt(browsableCol) == 0) continue;
                out.add(new Channel(c.getLong(idCol), c.getString(numCol), c.getString(nameCol)));
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        out.sort(new Comparator<Channel>() {
            @Override public int compare(Channel a, Channel b) {
                return compareChannelNumbers(a.number, b.number);
            }
        });
        return out;
    }

    public Result openLiveTv() {
        try {
            Intent category = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_TV)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (category.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(category);
                return new Result(true, "Abrindo TV ao vivo.");
            }
        } catch (Throwable ignored) { }

        List<Channel> channels = listChannels();
        if (!channels.isEmpty()) return tune(channels.get(0));
        if (!hasPermission()) return new Result(false, "Preciso da permissão de canais de TV na configuração inicial.");
        return new Result(false, "A TCL não expôs os canais ao aplicativo.");
    }

    public Result tuneByNumber(String requested) {
        if (!hasPermission()) return new Result(false, "Preciso da permissão de canais de TV na configuração inicial.");
        String target = normalizeNumber(requested);
        List<Channel> channels = listChannels();
        for (Channel channel : channels) {
            if (normalizeNumber(channel.number).equals(target)) return tune(channel);
        }
        for (Channel channel : channels) {
            if (normalizeNumber(channel.number).startsWith(target + ".")) return tune(channel);
        }
        return new Result(false, "Não encontrei o canal " + requested + ".");
    }

    public Result step(int direction) {
        if (!hasPermission()) return new Result(false, "Preciso da permissão de canais de TV na configuração inicial.");
        List<Channel> channels = listChannels();
        if (channels.isEmpty()) return new Result(false, "A TCL não expôs a lista de canais.");

        long lastId = context.getSharedPreferences("ei_chat_tv", Context.MODE_PRIVATE)
                .getLong("last_channel_id", -1L);
        int index = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).id == lastId) { index = i; break; }
        }
        int next;
        if (index < 0) next = direction >= 0 ? 0 : channels.size() - 1;
        else next = (index + (direction >= 0 ? 1 : -1) + channels.size()) % channels.size();
        return tune(channels.get(next));
    }

    private Result tune(Channel channel) {
        Uri uri = TvContract.buildChannelUri(channel.id);
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(view);
            context.getSharedPreferences("ei_chat_tv", Context.MODE_PRIVATE).edit()
                    .putLong("last_channel_id", channel.id)
                    .putString("last_channel_number", channel.number)
                    .apply();
            String label = channel.number.isEmpty() ? channel.name : channel.number;
            return new Result(true, "Canal " + label + ".");
        } catch (Throwable t) {
            return new Result(false, "O firmware não permitiu sintonizar esse canal por aplicativo.");
        }
    }

    private static String normalizeNumber(String value) {
        if (value == null) return "";
        return value.trim().replace(',', '.').replaceAll("\\s+", "");
    }

    private static int compareChannelNumbers(String a, String b) {
        double da = parseNumber(a);
        double db = parseNumber(b);
        if (!Double.isNaN(da) && !Double.isNaN(db)) return Double.compare(da, db);
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    private static double parseNumber(String value) {
        try { return Double.parseDouble(normalizeNumber(value)); }
        catch (Throwable ignored) { return Double.NaN; }
    }
}
