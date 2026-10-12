package com.magraoxx.eichattv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppCatalog {
    public static final class AppEntry {
        public final String label;
        public final String packageName;
        public final Intent launchIntent;

        AppEntry(String label, String packageName, Intent launchIntent) {
            this.label = label;
            this.packageName = packageName;
            this.launchIntent = launchIntent;
        }
    }

    private final Context context;
    private final List<AppEntry> apps = new ArrayList<>();
    private final Map<String, String> aliases = new HashMap<>();

    public AppCatalog(Context context) {
        this.context = context.getApplicationContext();
        aliases.put("prime", "prime video");
        aliases.put("amazon prime", "prime video");
        aliases.put("youtube kids", "youtube kids");
        aliases.put("disney", "disney+");
        aliases.put("disney plus", "disney+");
        aliases.put("max", "max");
        aliases.put("hbo", "max");
        aliases.put("globoplay", "globoplay");
        refresh();
    }

    public void refresh() {
        PackageManager pm = context.getPackageManager();
        LinkedHashMap<String, AppEntry> unique = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, unique);
        collect(pm, Intent.CATEGORY_LAUNCHER, unique);
        apps.clear();
        apps.addAll(unique.values());
    }

    private void collect(PackageManager pm, String category, Map<String, AppEntry> out) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(query, PackageManager.MATCH_ALL)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())) continue;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs == null ? pkg : labelCs.toString();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.put(pkg, new AppEntry(label, pkg, launch));
            }
        }
    }

    public List<AppEntry> getApps() {
        return new ArrayList<>(apps);
    }

    public AppEntry findBest(String spokenName) {
        String q = normalize(spokenName);
        if (aliases.containsKey(q)) q = normalize(aliases.get(q));
        AppEntry best = null;
        double bestScore = 0.0;
        for (AppEntry app : apps) {
            String name = normalize(app.label);
            double score;
            if (name.equals(q)) score = 1.0;
            else if (name.contains(q) || q.contains(name)) score = 0.92;
            else score = similarity(name, q);
            if (score > bestScore) {
                bestScore = score;
                best = app;
            }
        }
        return bestScore >= 0.58 ? best : null;
    }

    public boolean open(String spokenName) {
        AppEntry entry = findBest(spokenName);
        if (entry == null) return false;
        context.startActivity(entry.launchIntent);
        return true;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('+', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return s;
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int distance = levenshtein(a, b);
        return 1.0 - (distance / (double) Math.max(a.length(), b.length()));
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
