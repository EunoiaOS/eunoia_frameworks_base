/*
 * Copyright (C) 2023-2025 The EumoiaOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.eunoia;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.R;

import java.util.ArrayList;

import lineageos.providers.LineageSettings;

public abstract class LogoImage extends ImageView implements DarkReceiver {

    private Context mContext;

    private boolean mAttached;

    private boolean mShowLogo;
    private int mTintColor = Color.WHITE;

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    LineageSettings.System.getUriFor(LineageSettings.System.STATUS_BAR_LOGO), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public LogoImage(Context context) {
        this(context, null);
    }

    public LogoImage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    protected abstract boolean isLogoVisible();

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
        updateSettings();

        ((DarkIconDispatcher) Dependency.get(DarkIconDispatcher.class)).addDarkReceiver((DarkReceiver) this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

        mAttached = false;
        ((DarkIconDispatcher) Dependency.get(DarkIconDispatcher.class)).removeDarkReceiver((DarkReceiver) this);
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(areas, this, tint);
        if (mShowLogo && isLogoVisible()) {
            updateLogo();
        }
    }

    public void updateLogo() {
        Drawable drawable = mContext.getResources().getDrawable(R.drawable.ic_sblogo_eunoia);

        drawable.setTint(mTintColor);
        setImageDrawable(drawable);
    }

    public void updateSettings() {
        mShowLogo = LineageSettings.System.getIntForUser(mContext.getContentResolver(),
                LineageSettings.System.STATUS_BAR_LOGO, 0, UserHandle.USER_CURRENT) != 0;
        if (!mShowLogo || !isLogoVisible()) {
            setImageDrawable(null);
            setVisibility(View.GONE);
            return;
        }
        updateLogo();
        setVisibility(View.VISIBLE);
    }
}
