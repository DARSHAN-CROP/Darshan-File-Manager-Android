package com.darshan.filemanager;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** English / ગુજરાતી. The chosen language wraps every Context the app shows text from. */
final class Lang {
    private Lang() {}

    static Locale locale(String lang) { return "gu".equals(lang) ? new Locale("gu", "IN") : Locale.ENGLISH; }

    static Context wrap(Context base) {
        Locale want = locale(Store.lang(base));
        Configuration cur = base.getResources().getConfiguration();
        if (want.equals(cur.getLocales().get(0))) return base;
        Configuration cfg = new Configuration(cur);
        cfg.setLocale(want);
        return base.createConfigurationContext(cfg);
    }
}
