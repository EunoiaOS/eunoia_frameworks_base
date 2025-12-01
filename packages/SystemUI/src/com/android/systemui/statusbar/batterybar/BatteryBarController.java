/*
 * Copyright (C) 2023 The EunoiaOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.systemui.statusbar.batterybar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class BatteryBarController extends LinearLayout {

    private static final String TAG = "BatteryBarController";

    public static final int STYLE_REGULAR   = 0;
    public static final int STYLE_SYMMETRIC = 1;
    public static final int STYLE_REVERSE   = 2;

    private int mLocation = 0;
    private int mStyle = STYLE_REGULAR;
    private int mThickness = 2;

    private boolean mAttached = false;
    private boolean mVertical = false;

    private int mBatteryLevel = 0;
    private boolean mCharging = false;

    private int mLocationToLookFor = 0;

    private ContentObserver mSettingsObserver;

    public BatteryBarController(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            String ns = "http://schemas.android.com/apk/res/com.android.systemui";
            mLocationToLookFor = attrs.getAttributeIntValue(ns, "viewLocation", 0);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mAttached) return;
        mAttached = true;

        mVertical = getLayoutParams().height == LayoutParams.MATCH_PARENT;

        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        getContext().registerReceiver(mBatteryReceiver, f);

        registerSettingsObserver();
        loadSettings();
        rebuildBars();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (!mAttached) return;
        mAttached = false;

        try {
            getContext().unregisterReceiver(mBatteryReceiver);
        } catch (Exception ignored) {}

        unregisterSettingsObserver();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        post(this::rebuildBars);
    }

    private void loadSettings() {
        ContentResolver cr = getContext().getContentResolver();

        mLocation = Settings.System.getIntForUser(
                cr,
                "statusbar_battery_bar",
                0,
                UserHandle.USER_CURRENT
        );

        mStyle = Settings.System.getIntForUser(
                cr,
                "statusbar_battery_bar_style",
                STYLE_REGULAR,
                UserHandle.USER_CURRENT
        );

        mThickness = Settings.System.getIntForUser(
                cr,
                "statusbar_battery_bar_thickness",
                2,
                UserHandle.USER_CURRENT
        );
    }

    private void registerSettingsObserver() {
        if (mSettingsObserver != null) return;

        mSettingsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                post(() -> {
                    loadSettings();
                    rebuildBars();
                });
            }
        };

        ContentResolver cr = getContext().getContentResolver();

        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_style"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_thickness"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
    }

    private void unregisterSettingsObserver() {
        if (mSettingsObserver != null) {
            getContext().getContentResolver()
                    .unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
    }

    private void rebuildBars() {
        applyThickness();
        removeAllViews();

        if (mLocation == 0 || !isLocationValid(mLocation)) {
            Log.d(TAG, "BatteryBar disabled or wrong location");
            return;
        }

        Log.d(TAG, "Rebuild style=" + mStyle + " vertical=" + mVertical);

        if (mStyle == STYLE_REGULAR) {
            addView(createBar(), barParams());

        } else if (mStyle == STYLE_REVERSE) {
            BatteryBar bar = createBar();
            bar.setRotation(180);
            addView(bar, barParams());

        } else if (mStyle == STYLE_SYMMETRIC) {
            BatteryBar bar1 = createBar();
            BatteryBar bar2 = createBar();

            if (mVertical) {
                bar2.setRotation(180);
                addView(bar2, barParams());
                addView(bar1, barParams());
            } else {
                bar1.setRotation(180);
                addView(bar1, barParams());
                addView(bar2, barParams());
            }
        }

        requestLayout();
        invalidate();

        Log.d(TAG, "childCount=" + getChildCount());
    }

    private BatteryBar createBar() {
        return new BatteryBar(
                getContext(),
                mCharging,
                mBatteryLevel,
                mVertical
        );
    }

    private LayoutParams barParams() {
        return new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        );
    }

    private void applyThickness() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int px = Math.max(2, (int) (dm.density * mThickness + 0.5f));

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mVertical) lp.width = px;
        else lp.height = px;

        setLayoutParams(lp);
        requestLayout();
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            mBatteryLevel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mCharging = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                    == BatteryManager.BATTERY_STATUS_CHARGING;
        }
    };

    protected boolean isLocationValid(int location) {
        return mLocationToLookFor == 0 || mLocationToLookFor == location;
    }
}
