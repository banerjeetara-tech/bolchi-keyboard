package com.tara.bolchi;

import android.content.Context;

/** App version text for problem reports. */
final class BuildConfigInfo {
    private BuildConfigInfo() {}

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
