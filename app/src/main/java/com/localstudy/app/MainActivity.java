package com.localstudy.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.localstudy.app.ui.CalendarFragment;
import com.localstudy.app.ui.DirectoryFragment;
import com.localstudy.app.ui.Refreshable;
import com.localstudy.app.ui.SettingsFragment;
import com.localstudy.app.ui.StatsFragment;
import com.localstudy.app.ui.TodayFragment;
import com.localstudy.app.util.SettingsStore;
import com.localstudy.app.util.ThemeManager;
import com.localstudy.app.util.Ui;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_CALENDAR = "open_calendar";
    private static final String STATE_TAB = "selected_tab";
    private static final String[] TAGS = {"today", "calendar", "directory", "stats", "settings"};
    private static final int[] ICONS = {
            R.drawable.ic_home, R.drawable.ic_calendar, R.drawable.ic_book,
            R.drawable.ic_chart, R.drawable.ic_settings
    };
    private static final String[] LABELS = {"今日", "日历", "目录", "统计", "设置"};

    private FrameLayout content;
    private LinearLayout bottomBar;
    private final LinearLayout[] tabViews = new LinearLayout[TAGS.length];
    private int selectedTab;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.withFontScale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyBeforeSuper(this);
        super.onCreate(savedInstanceState);
        selectedTab = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0);
        buildLayout();
        restoreOrCreateFragments();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_CALENDAR, false)) {
            selectedTab = 1;
            getIntent().removeExtra(EXTRA_OPEN_CALENDAR);
        }
        selectTab(selectedTab, false);
        requestNotificationPermission();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        root.setClipToPadding(false);

        content = new FrameLayout(this);
        content.setId(R.id.study_content);
        content.setBackgroundColor(Ui.bg(this));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        bottomBar.setBackgroundColor(Ui.surfaceColor(this));
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 68)
        ));

        for (int i = 0; i < TAGS.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(LABELS[i]);
            item.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 3));
            ImageView icon = Ui.navIcon(this, ICONS[i]);
            TextView label = Ui.text(this, LABELS[i], 11, Ui.muted(this), false);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(this, 2), 0, 0);
            item.addView(icon);
            item.addView(label);
            item.setOnClickListener(view -> selectTab(index, true));
            bottomBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            tabViews[i] = item;
        }
        setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), root
        );
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
        ViewCompat.requestApplyInsets(root);
    }

    private void restoreOrCreateFragments() {
        if (getSupportFragmentManager().findFragmentByTag(TAGS[0]) != null) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment[] fragments = {
                new TodayFragment(), new CalendarFragment(), new DirectoryFragment(),
                new StatsFragment(), new SettingsFragment()
        };
        for (int i = 0; i < fragments.length; i++) {
            transaction.add(R.id.study_content, fragments[i], TAGS[i]);
        }
        transaction.commitNow();
    }

    private void selectTab(int index, boolean refresh) {
        selectedTab = Math.max(0, Math.min(TAGS.length - 1, index));
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (int i = 0; i < TAGS.length; i++) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAGS[i]);
            if (fragment == null) continue;
            if (i == selectedTab) {
                transaction.show(fragment);
            } else {
                transaction.hide(fragment);
            }
        }
        transaction.commit();
        updateBottomTabs();
        Fragment current = getSupportFragmentManager().findFragmentByTag(TAGS[selectedTab]);
        if (refresh && current instanceof Refreshable) {
            ((Refreshable) current).refresh();
        }
    }

    private void updateBottomTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean selected = i == selectedTab;
            LinearLayout item = tabViews[i];
            ImageView icon = (ImageView) item.getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            int color = selected ? Ui.accent(this) : Ui.muted(this);
            icon.setColorFilter(color);
            label.setTextColor(color);
            item.setBackgroundColor(selected ? Ui.withAlpha(Ui.accent(this), 18) : Color.TRANSPARENT);
        }
    }

    public void selectTabByIndex(int index) {
        selectTab(index, true);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_CALENDAR, false)) {
            selectTab(1, true);
            intent.removeExtra(EXTRA_OPEN_CALENDAR);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    901
            );
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }
}
