package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Ordered, explicitly chosen app favourites, independent of theme resets and launch timestamps. */
final class Favorites {
    static final int MAX = 12;
    private static final String PREFS = "personal_favourites", KEY = "packages";
    static List<String> load(Context ctx) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String pkg = array.optString(i);
                if (pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") && !result.contains(pkg)) result.add(pkg);
            }
        } catch (org.json.JSONException ignored) { }
        return result;
    }
    static boolean toggle(Context ctx, String pkg, int limit) {
        List<String> packages = load(ctx);
        if (!packages.remove(pkg)) {
            if (packages.size() >= limit) return false;
            packages.add(pkg);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, new JSONArray(packages).toString()).apply();
        return true;
    }
    static void saveOrder(Context ctx, List<String> componentKeys) {
        List<String> current = load(ctx), ordered = new ArrayList<>();
        for (String key : componentKeys) {
            int slash = key.indexOf('/'); String pkg = slash < 0 ? key : key.substring(0, slash);
            if (current.contains(pkg) && !ordered.contains(pkg)) ordered.add(pkg);
        }
        for (String pkg : current) if (!ordered.contains(pkg)) ordered.add(pkg);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, new JSONArray(ordered).toString()).apply();
    }
    static void clear(Context ctx) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply(); }
}
