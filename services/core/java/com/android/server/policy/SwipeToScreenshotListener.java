/*
 * Copyright (C) 2023 The  Project
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

package com.android.server.policy;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

import androidx.annotation.NonNull;

public final class SwipeToScreenshotListener implements PointerEventListener {

    private static final int STATE_IDLE       = 0;
    private static final int STATE_TRACKING   = 1;
    private static final int STATE_CONSUMED   = 2;
    private static final int STATE_FAILED     = 3;

    private static final int REQUIRED_POINTERS = 3;
    private static final long MAX_START_TIME_MS = 400;

    private final Context mContext;
    private final Callbacks mCallbacks;
    private final InputManager mInputManager;
    private final DisplayMetrics mMetrics;

    private final int mTouchSlopPx;
    private final int mTriggerDistancePx;

    private final int[] mPointerIds = new int[REQUIRED_POINTERS];
    private final float[] mStartY = new float[REQUIRED_POINTERS];

    private int mState = STATE_IDLE;
    private boolean mBootCompleted;
    private boolean mDeviceProvisioned;

    public SwipeToScreenshotListener(
            @NonNull Context context,
            @NonNull Callbacks callbacks) {

        mContext = context;
        mCallbacks = callbacks;
        mInputManager = context.getSystemService(InputManager.class);
        mMetrics = context.getResources().getDisplayMetrics();

        mTouchSlopPx = dpToPx(16);
        mTriggerDistancePx = dpToPx(140);
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if (!isReady()) return;

        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            reset();
            return;
        }

        if (mState == STATE_IDLE && event.getPointerCount() == REQUIRED_POINTERS) {
            if (canStartGesture(event)) {
                startTracking(event);
            } else {
                mState = STATE_FAILED;
            }
            return;
        }

        if (mState != STATE_TRACKING) return;

        if (event.getPointerCount() != REQUIRED_POINTERS) {
            mState = STATE_FAILED;
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            handleMove(event);
        }
    }

    private void startTracking(MotionEvent event) {
        mState = STATE_TRACKING;
        for (int i = 0; i < REQUIRED_POINTERS; i++) {
            mPointerIds[i] = event.getPointerId(i);
            mStartY[i] = event.getY(i);
        }
    }

    private void handleMove(MotionEvent event) {
        float dySum = 0f;
        float dxSum = 0f;

        for (int i = 0; i < REQUIRED_POINTERS; i++) {
            int index = event.findPointerIndex(mPointerIds[i]);
            if (index < 0) {
                mState = STATE_FAILED;
                return;
            }

            dySum += event.getY(index) - mStartY[i];
            dxSum += Math.abs(event.getX(index)
                    - event.getHistoricalX(index, 0));
        }

        // Early direction lock → kill scroll ASAP
        if (dySum > mTouchSlopPx && dySum > dxSum * 1.5f) {
            cancelTouch();
        }

        if (dySum >= mTriggerDistancePx) {
            mState = STATE_CONSUMED;
            cancelTouch();
            mCallbacks.onSwipeThreeFinger();
        }
    }

    private boolean canStartGesture(MotionEvent event) {
        if (event.getEventTime() - event.getDownTime() > MAX_START_TIME_MS) {
            return false;
        }

        final int height = mMetrics.heightPixels;
        final float bottomReject = height - dpToPx(48);

        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (int i = 0; i < REQUIRED_POINTERS; i++) {
            float y = event.getY(i);
            if (y > bottomReject) return false;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        return (maxY - minY) <= dpToPx(150);
    }

    private boolean isReady() {
        if (!mBootCompleted) {
            mBootCompleted = SystemProperties.getBoolean(
                    "sys.boot_completed", false);
            return false;
        }

        if (!mDeviceProvisioned) {
            mDeviceProvisioned = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONED, 0) != 0;
            return false;
        }
        return true;
    }

    private void reset() {
        mState = STATE_IDLE;
    }

    private void cancelTouch() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(
                now, now,
                MotionEvent.ACTION_CANCEL,
                0f, 0f, 0);

        cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mInputManager.injectInputEvent(
                cancel,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        cancel.recycle();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * mMetrics.density);
    }

    public interface Callbacks {
        void onSwipeThreeFinger();
    }
}
