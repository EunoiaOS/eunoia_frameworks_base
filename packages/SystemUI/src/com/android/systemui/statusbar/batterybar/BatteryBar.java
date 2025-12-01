/*
 * Copyright (C) 2023 The EunoiaOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.systemui.statusbar.batterybar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class BatteryBar extends RelativeLayout {

    private static final String TAG = "BatteryBar";

    private static final int DEFAULT_COLOR = 0xFF00FF00;
    private static final int DEFAULT_CHARGING_COLOR = 0xFF00BCD4;
    private static final int DEFAULT_LOW_COLOR = 0xFFFF0000;

    private static final int BATTERY_LOW_VALUE = 20;
    private static final int ANIM_DURATION = 1000;

    private int mBatteryLevel = 0;
    private boolean mCharging = false;
    private boolean mVertical = false;
    private boolean mAnimating = false;

    private LinearLayout mBarLayout;
    private View mBar;

    private LinearLayout mChargerLayout;
    private View mCharger;

    private ContentObserver mSettingsObserver;

    public BatteryBar(Context context) {
        this(context, null);
    }

    public BatteryBar(Context context, boolean charging, int level, boolean vertical) {
        this(context, null);
        mCharging = charging;
        mBatteryLevel = level;
        mVertical = vertical;
    }

    public BatteryBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    private void init() {
        setClipChildren(false);
        setClipToPadding(false);

        setBackgroundColor(0x2200FF00);

        mBarLayout = new LinearLayout(mContext);
        addView(mBarLayout, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        mBar = new View(mContext);
        mBarLayout.addView(mBar, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int size = Math.max(2, (int) (dm.density * 4 + 0.5f));

        mChargerLayout = new LinearLayout(mContext);
        LayoutParams lp = mVertical
                ? new LayoutParams(LayoutParams.MATCH_PARENT, size)
                : new LayoutParams(size, LayoutParams.MATCH_PARENT);
        addView(mChargerLayout, lp);

        mCharger = new View(mContext);
        mChargerLayout.addView(
                mCharger,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );

        mChargerLayout.setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        getContext().registerReceiver(mBatteryReceiver, f);

        registerSettingsObserver();

        post(() -> applyProgress(mBatteryLevel));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        try {
            getContext().unregisterReceiver(mBatteryReceiver);
        } catch (Exception ignored) {}

        unregisterSettingsObserver();
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                    == BatteryManager.BATTERY_STATUS_CHARGING;

            applyProgress(mBatteryLevel);

            if (mCharging && mBatteryLevel < 100) {
                startAnim();
            } else {
                stopAnim();
            }
        }
    };

    private void registerSettingsObserver() {
        if (mSettingsObserver != null) return;

        mSettingsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                post(() -> applyProgress(mBatteryLevel));
            }
        };

        ContentResolver cr = mContext.getContentResolver();

        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_color"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_charging_color"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_battery_low_color"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_blend_color"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        cr.registerContentObserver(
                Settings.System.getUriFor("statusbar_battery_bar_blend_color_reverse"),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
    }

    private void unregisterSettingsObserver() {
        if (mSettingsObserver != null) {
            mContext.getContentResolver()
                    .unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
    }

    private void applyProgress(int level) {
        if (getWidth() == 0 && getHeight() == 0) {
            post(() -> applyProgress(level));
            return;
        }

        if (mVertical) {
            int h = (int) (getHeight() * (level / 100f));
            LayoutParams p = (LayoutParams) mBarLayout.getLayoutParams();
            p.height = Math.max(h, 2);
            mBarLayout.setLayoutParams(p);
        } else {
            int w = (int) (getWidth() * (level / 100f));
            LayoutParams p = (LayoutParams) mBarLayout.getLayoutParams();
            p.width = Math.max(w, 2);
            mBarLayout.setLayoutParams(p);
        }

        int color = resolveColor(level);
        Log.d(TAG, "apply color=0x" + Integer.toHexString(color));

        mBar.setBackgroundColor(color);
        mCharger.setBackgroundColor(color);

        invalidate();
    }

    private int resolveColor(int level) {
        int normal = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "statusbar_battery_bar_color",
                DEFAULT_COLOR,
                UserHandle.USER_CURRENT
        );

        int charging = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "statusbar_battery_bar_charging_color",
                DEFAULT_CHARGING_COLOR,
                UserHandle.USER_CURRENT
        );

        int low = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "statusbar_battery_bar_battery_low_color",
                DEFAULT_LOW_COLOR,
                UserHandle.USER_CURRENT
        );

        int blend = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "statusbar_battery_bar_blend_color",
                0,
                UserHandle.USER_CURRENT
        );

        int reversed = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "statusbar_battery_bar_blend_color_reverse",
                0,
                UserHandle.USER_CURRENT
        );

        if (mCharging) return charging;
        if (blend != 0) {
            return getBlendColor(normal, low, reversed, level);
        } else {
            return level <= BATTERY_LOW_VALUE ? low : normal;
        }
    }

    private int getBlendColor(int fullColor, int lowColor, int reversed, int level) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(lowColor, empty);
        int emptyAlpha = Color.alpha(lowColor);
        float blendFactor = level/100f;
        if (reversed != 0) {
            if (empty[0] < full[0]) {
                empty[0] += 360f;
            }
            newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
            if (empty[0] > full[0]) {
                    full[0] += 360f;
            }
            newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }

    private void startAnim() {
        if (mAnimating) return;

        TranslateAnimation a;
        if (mVertical) {
            a = new TranslateAnimation(0, 0, getHeight(), 0);
        } else {
            a = new TranslateAnimation(getWidth(), 0, 0, 0);
        }

        a.setDuration(ANIM_DURATION);
        a.setRepeatCount(Animation.INFINITE);
        a.setInterpolator(new AccelerateInterpolator());

        mChargerLayout.setVisibility(VISIBLE);
        mChargerLayout.startAnimation(a);
        mAnimating = true;
    }

    private void stopAnim() {
        mChargerLayout.clearAnimation();
        mChargerLayout.setVisibility(GONE);
        mAnimating = false;
    }
}
