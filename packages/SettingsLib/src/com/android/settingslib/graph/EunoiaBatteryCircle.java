/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The EunoiaOS Project
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

package com.android.settingslib.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.android.settingslib.R;

public class EunoiaBatteryCircle extends Drawable {
    private static final int FULL_COLOR = 0xFF3CD387;
    private static final int LOW_COLOR = 0xFFFD7267;
    private static final int CHARGING_COLOR = 0xFF2EC4C6;

    private static final int PERCENTAGE_LIGHT = 0xFF1E1E1E;
    private static final int PERCENTAGE_DARK = 0xFFEDEDED;

    private static final int LOW_BATTERY_LEVEL = 20;
    private static final long PULSE_DURATION = 1200;

    private int mLevel = 100;

    private final Paint mColor;
    private final Paint mIndicator;

    private final Rect mTextBounds = new Rect();
    private final Path mClipPath = new Path();

    private boolean mCharging = false;
    private boolean mPulsing;
    private long mPulseStart;

    private Typeface mTypeface;

    public EunoiaBatteryCircle(Context context) {
        mTypeface = ResourcesCompat.getFont(context, R.font.fifa_welcome);
        mColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        mColor.setStyle(Paint.Style.FILL);

        mIndicator = new Paint(Paint.ANTI_ALIAS_FLAG);
        mIndicator.setColor(PERCENTAGE_DARK);
        mIndicator.setTextAlign(Paint.Align.CENTER);
        mIndicator.setFakeBoldText(true);
        if (mTypeface != null) {
            mIndicator.setTypeface(mTypeface);
        }
    }

    private float getPulse() {
        if (!mPulsing) return 1f;

        long elapsed = (System.currentTimeMillis() - mPulseStart) % PULSE_DURATION;
        float phase = (float) elapsed / PULSE_DURATION;

        return 1f + 0.15f * (float) Math.sin(phase * Math.PI * 2);
    }

    private float resolveTextSize(float radius, String text) {
        if (text.length() >= 3) {
            return radius * 0.95f;
        } else if (text.length() == 2) {
            return radius * 1.10f;
        } else {
            return radius * 1.20f;
        }
    }

    public void setBatteryLevel(int level) {
        mLevel = Math.max(0, Math.min(level, 100));
        updateColor(FULL_COLOR, LOW_COLOR, level);
        invalidateSelf();
    }

    public void setCharging(boolean charging) {
        if (mCharging != charging) {
            mCharging = charging;

            if (charging) {
                mPulseStart = System.currentTimeMillis();
                mPulsing = true;
            } else {
                mPulsing = false;
            }
            invalidateSelf();
        }
    }

    public void setColor(float darkIntensity) {
        if (darkIntensity < 0.5) {
            mIndicator.setColor(PERCENTAGE_DARK);
        } else {
            mIndicator.setColor(PERCENTAGE_LIGHT);
        }
        invalidateSelf();
    }

    private void updateColor(int fullColor, int lowColor, int level) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];

        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);

        Color.colorToHSV(lowColor, empty);
        int emptyAlpha = Color.alpha(lowColor);

        float blendFactor = level/100f;

        if (empty[0] > full[0]) {
            full[0] += 360f;
        }
        newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;

        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }

        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));

        if (mCharging) {
            mColor.setColor(CHARGING_COLOR);
        } else {
            mColor.setColor(Color.HSVToColor(newAlpha, newColor));
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();

        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float radius = Math.min(b.width(), b.height()) / 2f;

        float fillHeight = (mLevel / 100f) * (radius * 2);
        float top = b.bottom - fillHeight;

        mClipPath.reset();
        mClipPath.addCircle(cx, cy, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(mClipPath);
        canvas.clipRect(b.left, top, b.right, b.bottom);
        canvas.drawRect(b, mColor);
        canvas.restore();

        String percentage = mCharging ? "\u26A1" : String.valueOf(mLevel);
        float baseSize = mCharging ? radius * 1.25f : radius * 1.05f;
        float pulse = mCharging ? getPulse() : 1f;
        float textSize = baseSize * pulse;

        mIndicator.setTextSize(textSize);
        if (mCharging) {
            int alpha = (int) (200 + 55 * pulse);
            mIndicator.setAlpha(alpha);
        } else {
            mIndicator.setAlpha(255);
        }

        Paint.FontMetrics fm = mIndicator.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(percentage, cx, textY, mIndicator);

        if (mCharging) {
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mColor.setAlpha(alpha);
        mIndicator.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        mColor.setColorFilter(colorFilter);
        mIndicator.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}

