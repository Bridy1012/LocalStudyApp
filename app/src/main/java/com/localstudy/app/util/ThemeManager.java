package com.localstudy.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.StyleRes;

import com.localstudy.app.R;

public final class ThemeManager {
    private ThemeManager() {
    }

    @StyleRes
    public static int themeRes(Context context) {
        switch (SettingsStore.accent(context)) {
            case "blue":
                return R.style.Theme_LocalStudy_Blue;
            case "orange":
                return R.style.Theme_LocalStudy_Orange;
            case "plum":
                return R.style.Theme_LocalStudy_Plum;
            case "coral":
                return R.style.Theme_LocalStudy_Coral;
            case "gold":
                return R.style.Theme_LocalStudy_Gold;
            default:
                return R.style.Theme_LocalStudy_Teal;
        }
    }

    public static void applyBeforeSuper(Activity activity) {
        activity.setTheme(themeRes(activity));
    }

    public static Context withFontScale(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = SettingsStore.fontScale(context);
        return context.createConfigurationContext(configuration);
    }
}
