/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.View;
import android.util.Slog;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.NotificationManager;
import android.app.Notification;


import com.android.systemui.R;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";
    private static final int CHARGE_NOTIFICATION_ID = 555;
    private static final int DISCHARGE_NOTIFICATION_ID = 556;

    private static int cur_status = BatteryManager.BATTERY_STATUS_DISCHARGING;
    private static int old_status = BatteryManager.BATTERY_STATUS_DISCHARGING;

    /// M: zhangsx10 BLADEFHD-591 show battery percentage
    private static final String ACTION_BATTERY_PERCENTAGE_SWITCH = "lenovo.intent.action.BATTERY_PERCENTAGE_SWITCH";
    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();
    /// M: zhangsx10 BLADEFHD-591 show battery percentage @{
    private boolean mShouldShowBatteryPercentage = false;
    private String mBatteryPercentage = "100%";
    /// @}
    private final NotificationManager mNotificationManager;
    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();
    private int mIcon;

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        mContext = context;
        /// M: zhangsx10 BLADEFHD-591 show battery percentage @{
        mShouldShowBatteryPercentage = (Settings.Secure.getInt(context
                .getContentResolver(), Settings.Secure.BATTERY_PERCENTAGE, 0) != 0);
        Slog.d(TAG, "BatteryController mShouldShowBatteryPercentage is "
                + mShouldShowBatteryPercentage);
        /// @}

        mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        /// M: zhangsx10 BLADEFHD-591 show battery percentage
        filter.addAction(ACTION_BATTERY_PERCENTAGE_SWITCH);
        filter.addAction(Intent.ACTION_USER_SWITCHED);//add lenovo-sw gengse1 2014/07/09 BLADEFHDKK-834
        context.registerReceiver(this, filter);
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    /// M: zhangsx10 BLADEFHD-591 show battery percentage @{
    private  String getBatteryPercentage(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra("level", 0);
        int scale = batteryChangedIntent.getIntExtra("scale", 100);
        //when Lock screen and charging,
        //battery percentage show is different between systemui and screen in Arabic language
        int value = level * 100 / scale;
        String batteryPercentage = mContext.getString(R.string.battery_percentage, value);
        return batteryPercentage;
    }
    /// @}

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);

            boolean plugged = false;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                case BatteryManager.BATTERY_STATUS_FULL:
                    plugged = true;
                    break;
            }
            final boolean fulled = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) == 100;
            mIcon = plugged ? R.drawable.stat_sys_battery_charge
                                     : R.drawable.stat_sys_battery;
            Notification notification = new Notification();
            notification.flags = Notification.FLAG_SHOW_LIGHTS;
            if (plugged && !fulled) {
                cur_status = BatteryManager.BATTERY_STATUS_CHARGING;
            } else {
                cur_status = BatteryManager.BATTERY_STATUS_DISCHARGING;
            }

            if (cur_status != old_status) {
                if (cur_status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    notification.ledARGB = 0xffffffff;
                    notification.ledOnMS = 2000;
                    notification.ledOffMS = 0;
                    mNotificationManager.notify(CHARGE_NOTIFICATION_ID, notification);
                } else {
                    notification.ledARGB = 0xff000000;
                    mNotificationManager.notify(DISCHARGE_NOTIFICATION_ID, notification);
                }

                old_status = cur_status;
            }
            int N = mIconViews.size();
            for (int i=0; i<N; i++) {
                ImageView v = mIconViews.get(i);
                v.setImageResource(mIcon);
                v.setImageLevel(level);
                v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                        level));
            }
            N = mLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mLabelViews.get(i);
                v.setText(mContext.getString(R.string.status_bar_settings_battery_meter_format,
                        level));
            }

            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(level, plugged);
            }

            /// M: zhangsx10 BLADEFHD-591 show battery percentage @{
            mBatteryPercentage = getBatteryPercentage(intent);
            Slog.d(TAG,"mBatteryPercentage is " + mBatteryPercentage + " mShouldShowBatteryPercentage is "
                    + mShouldShowBatteryPercentage + " mLabelViews.size() " + mLabelViews.size());
            TextView v = mLabelViews.get(0);
            if (mShouldShowBatteryPercentage) {
                v.setText(mBatteryPercentage);
                v.setVisibility(View.VISIBLE);
            } else {
                v.setVisibility(View.GONE);
            }
            /// @}
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            if (mIcon != 0) {
                int N = mIconViews.size();
                for (int i=0; i<N; i++) {
                    ImageView v = mIconViews.get(i);
                    v.setImageDrawable(null);
                    v.setImageResource(mIcon);
                }
            }
        }
        /// M: zhangsx10 BLADEFHD-591 show battery percentage @{
        //modify lenovo-sw gengse1 2014/07/09 BLADEFHDKK-834 begin
        else if ((action.equals(ACTION_BATTERY_PERCENTAGE_SWITCH))
                || (action.equals(Intent.ACTION_USER_SWITCHED))) {
            mShouldShowBatteryPercentage = (Settings.Secure.getInt(context
                    .getContentResolver(), Settings.Secure.BATTERY_PERCENTAGE, 0) != 0);
            Slog.d(TAG, "onReceive intent " + action + " ,mShouldShowBatteryPercentage = " + mShouldShowBatteryPercentage);
            TextView v = mLabelViews.get(0);
            if (mShouldShowBatteryPercentage) {
                v.setText(mBatteryPercentage);
                v.setVisibility(View.VISIBLE);
            } else {
                v.setVisibility(View.GONE);
            }
        }//modify lenovo-sw gengse1 2014/07/09 BLADEFHDKK-834 end
        /// @}
    }
}
