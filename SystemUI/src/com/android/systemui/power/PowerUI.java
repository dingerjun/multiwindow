/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.power;

import java.util.Timer;
import java.util.TimerTask;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.SystemUI;

import android.content.pm.PackageManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

public class PowerUI extends SystemUI {
    static final String TAG = "PowerUI";

    static final boolean DEBUG = false;

    Handler mHandler = new Handler();

    int mBatteryLevel = 100;
    int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    int mPlugType = 0;
    int mInvalidCharger = 0;
    int mTemperature = 13;

    int mLowBatteryAlertCloseLevel;
    int[] mLowBatteryReminderLevels = new int[2];

    AlertDialog mInvalidChargerDialog;
    AlertDialog mLowBatteryDialog;
    TextView mBatteryLevelTextView;
    private static final int mLowBateryFlagUsbHost = 30;
    private long mScreenOffTime = -1;

    public void start() {

        mLowBatteryAlertCloseLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryCloseWarningLevel);
        mLowBatteryReminderLevels[0] = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryReminderLevels[1] = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mScreenOffTime = pm.isScreenOn() ? -1 : SystemClock.elapsedRealtime();

        // Register for Intent broadcasts for...
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);
    }

    /**
     * Buckets the battery level.
     *
     * The code in this function is a little weird because I couldn't comprehend
     * the bucket going up when the battery level was going down. --joeo
     *
     * 1 means that the battery is "ok"
     * 0 means that the battery is between "ok" and what we should warn about.
     * less than 0 means that the battery is low
     */
    private int findBatteryLevelBucket(int level) {
        if (level >= mLowBatteryAlertCloseLevel) {
            return 1;
        }
        if (level >= mLowBatteryReminderLevels[0]) {
            return 0;
        }
        final int N = mLowBatteryReminderLevels.length;
        for (int i=N-1; i>=0; i--) {
            if (level <= mLowBatteryReminderLevels[i]) {
                return -1-i;
            }
        }
        throw new RuntimeException("not possible!");
    }

    /** BEGIN [BLADEFHD-41][wangyq12] Energy bay feature development **/
    private static final String KeyFlagLowBateryUsbhost = "bFlagLowBateryUsbhost" ;
    private final void saveFlagLowBateryUsbhost(){
        boolean bopenUsbHost = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        if(!bopenUsbHost)return ;
        int bFlagLowBateryUsbhost = -1 ;
        if(mBatteryLevel <= mLowBateryFlagUsbHost){
             bFlagLowBateryUsbhost = 1 ;
        }else{
             bFlagLowBateryUsbhost = 0 ;
        }
        int value = Settings.System.getInt(mContext.getContentResolver(),KeyFlagLowBateryUsbhost,-1);
        if(value != bFlagLowBateryUsbhost){
           Settings.System.putInt(mContext.getContentResolver(),KeyFlagLowBateryUsbhost,bFlagLowBateryUsbhost);
       }
    }
    /** END [BLADEFHD-41][wangyq12] Energy bay feature development **/

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                final int oldBatteryLevel = mBatteryLevel;
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                final int oldBatteryStatus = mBatteryStatus;
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                final int oldPlugType = mPlugType;
                mPlugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);
                final int oldInvalidCharger = mInvalidCharger;
                mInvalidCharger = intent.getIntExtra(BatteryManager.EXTRA_INVALID_CHARGER, 0);

                final boolean plugged = mPlugType != 0;
                final boolean oldPlugged = oldPlugType != 0;
                saveFlagLowBateryUsbhost();

                int oldBucket = findBatteryLevelBucket(oldBatteryLevel);
                int bucket = findBatteryLevelBucket(mBatteryLevel);
                
                final int oldTemperature = mTemperature;
                mTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 13);
                if (DEBUG) {
                    Slog.d(TAG, "buckets   ....." + mLowBatteryAlertCloseLevel
                            + " .. " + mLowBatteryReminderLevels[0]
                            + " .. " + mLowBatteryReminderLevels[1]);
                    Slog.d(TAG, "level          " + oldBatteryLevel + " --> " + mBatteryLevel);
                    Slog.d(TAG, "status         " + oldBatteryStatus + " --> " + mBatteryStatus);
                    Slog.d(TAG, "plugType       " + oldPlugType + " --> " + mPlugType);
                    Slog.d(TAG, "invalidCharger " + oldInvalidCharger + " --> " + mInvalidCharger);
                    Slog.d(TAG, "bucket         " + oldBucket + " --> " + bucket);
                    Slog.d(TAG, "plugged        " + oldPlugged + " --> " + plugged);
                    Slog.d(TAG, "temperature    " + oldTemperature + " --> " + mTemperature);
                }

                if (mTemperature > TEMP_TOO_HIGH) {
                    Slog.d(TAG, "showing mTemperature charger warning > 59");
                    mTemperatureHandler.removeMessages(MSG_TEMP_HIGH);
                    mTemperatureHandler.removeMessages(MSG_TEMP_LOW);
                    if (!mTemperatureHandler.hasMessages(MSG_TEMP_TOO_HIGH)) mTemperatureHandler.sendEmptyMessageDelayed(MSG_TEMP_TOO_HIGH, 3000);
                } else if (mTemperature > TEMP_HIGH) {
                    Slog.d(TAG, "showing mTemperature charger warning > 50");
                    mTemperatureHandler.removeMessages(MSG_TEMP_TOO_HIGH);
                    mTemperatureHandler.removeMessages(MSG_TEMP_LOW);
                    if (plugged && !mTemperatureHandler.hasMessages(MSG_TEMP_HIGH)) mTemperatureHandler.sendEmptyMessageDelayed(MSG_TEMP_HIGH, 3000);
                } else if (mTemperature < TEMP_LOW){
                    Slog.d(TAG, "showing mTemperature charger warning < 0");
                    mTemperatureHandler.removeMessages(MSG_TEMP_TOO_HIGH);
                    mTemperatureHandler.removeMessages(MSG_TEMP_HIGH);
                    if (plugged && !mTemperatureHandler.hasMessages(MSG_TEMP_LOW)) mTemperatureHandler.sendEmptyMessageDelayed(MSG_TEMP_LOW, 10000);
                } else {
                    Slog.d(TAG, "showing mTemperature charger warning other");
                    mTemperatureHandler.removeMessages(MSG_TEMP_TOO_HIGH);
                    mTemperatureHandler.removeMessages(MSG_TEMP_HIGH);
                    mTemperatureHandler.removeMessages(MSG_TEMP_LOW);
                }

                if (oldInvalidCharger == 0 && mInvalidCharger != 0) {
                    Slog.d(TAG, "showing invalid charger warning");
                    showInvalidChargerDialog();
                    return;
                } else if (oldInvalidCharger != 0 && mInvalidCharger == 0) {
                    dismissInvalidChargerDialog();
                } else if (mInvalidChargerDialog != null) {
                    // if invalid charger is showing, don't show low battery
                    return;
                }

                if (!plugged
                        && (bucket < oldBucket || oldPlugged)
                        && mBatteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                        && bucket < 0) {
                    showLowBatteryWarning();

                    // only play SFX when the dialog comes up or the bucket changes
                    if (bucket != oldBucket || oldPlugged) {
                        playLowBatterySound();
                    }
                } else if (plugged || (bucket > oldBucket && bucket > 0)) {
                    dismissLowBatteryWarning();
                } else if (mBatteryLevelTextView != null) {
                    showLowBatteryWarning();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOffTime = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOffTime = -1;
            } else {
                Slog.w(TAG, "unknown intent: " + intent);
            }
        }
    };

    private static final int TEMP_TOO_HIGH = 590;
    private static final int TEMP_HIGH = 500;
    private static final int TEMP_LOW = 0;
    private static final int MSG_TEMP_TOO_HIGH = 1301;
    private static final int MSG_TEMP_HIGH = 1302;
    private static final int MSG_TEMP_LOW = 1303;
    private static final int MSG_SHUTDOWN = 1304;
    private AlertDialog mTemperatureDialog = null;

    Handler mTemperatureHandler = new Handler() { 
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_TEMP_TOO_HIGH:
                showTempTooHighWarning();
                break;
            case MSG_TEMP_HIGH:
                showTempHighWarning();
                break;
            case MSG_TEMP_LOW:
                showTempLowWarning();
                break;
            case MSG_SHUTDOWN:
                shutdownIfTempHigh(mContext, false);
                break;
            default:
                break;
            }
        }
    };

    void showTempTooHighWarning() {
        Log.e(TAG, "temp too high, later shutdown");
        playLowBatterySound();
        if (mTemperatureDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext, com.android.internal.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            builder.setCancelable(true);
            builder.setTitle(R.string.temperature_too_high_title);
            builder.setMessage(R.string.temperature_too_too_high);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(android.R.string.ok, null);
            mTemperatureDialog = builder.create();
            mTemperatureDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        mTemperatureDialog = null;
                    }
                });
            mTemperatureDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mTemperatureDialog.show();
        } else {
            mTemperatureDialog.show();
        }
        if (!mTemperatureHandler.hasMessages(MSG_SHUTDOWN)){
            mTemperatureHandler.sendEmptyMessageDelayed(MSG_SHUTDOWN, 5000);
        }
    }

    public static final void shutdownIfTempHigh(Context context, boolean isConfrim) {
        Log.i(TAG, "5 seconds past! <-- Warning! System will shutdown! -->");
        if (ActivityManagerNative.isSystemReady()) {
            Log.i(TAG, "[shutdownIfNoPower()] <-- Warning! System will shutdown! -->");
            Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
            intent.putExtra(Intent.EXTRA_KEY_CONFIRM, isConfrim);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    void showTempHighWarning() {
        Log.e(TAG, "temp too high");
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, com.android.internal.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setCancelable(true);
        builder.setTitle(R.string.temperature_too_high_title);
        builder.setMessage(R.string.temperature_too_high);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(android.R.string.ok, null);
        final Dialog a = builder.create();
        a.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        a.show();

        (new Timer()).schedule(new TimerTask() {
            public void run() {
                if (a != null)a.dismiss();
            }
        }, 5000);
    }

    void showTempLowWarning() {
        Log.e(TAG, "temp too low");
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, com.android.internal.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setCancelable(true);
        builder.setTitle(R.string.temperature_too_low_title);
        builder.setMessage(R.string.temperature_too_low);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(android.R.string.ok, null);
        final Dialog a = builder.create();
        a.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        a.show();
        (new Timer()).schedule(new TimerTask() {
            public void run() {
                if (a != null)a.dismiss();
            }
        }, 5000);
    }

    void dismissLowBatteryWarning() {
        if (mLowBatteryDialog != null) {
            Slog.i(TAG, "closing low battery warning: level=" + mBatteryLevel);
            mLowBatteryDialog.dismiss();
        }
    }

    void showLowBatteryWarning() {
        Slog.i(TAG,
                ((mBatteryLevelTextView == null) ? "showing" : "updating")
                + " low battery warning: level=" + mBatteryLevel
                + " [" + findBatteryLevelBucket(mBatteryLevel) + "]");

        CharSequence levelText = mContext.getString(
                R.string.battery_low_percent_format, mBatteryLevel);

        if (mBatteryLevelTextView != null) {
            mBatteryLevelTextView.setText(levelText);
        } else {
            View v = View.inflate(mContext, R.layout.battery_low, null);
            mBatteryLevelTextView = (TextView)v.findViewById(R.id.level_percent);

            mBatteryLevelTextView.setText(levelText);

            AlertDialog.Builder b = new AlertDialog.Builder(mContext, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                b.setCancelable(true);
                b.setTitle(R.string.battery_low_title);
                b.setView(v);
                b.setIconAttribute(android.R.attr.alertDialogIcon);
                b.setPositiveButton(android.R.string.ok, null);

            final Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);
            if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                b.setNegativeButton(R.string.battery_low_why,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                        dismissLowBatteryWarning();
                    }
                });
            }

            AlertDialog d = b.create();
            d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mLowBatteryDialog = null;
                        mBatteryLevelTextView = null;
                    }
                });
            d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            d.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            d.show();
            mLowBatteryDialog = d;
        }
    }

    void playLowBatterySound() {
        final ContentResolver cr = mContext.getContentResolver();

        final int silenceAfter = Settings.Global.getInt(cr,
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0);
        final long offTime = SystemClock.elapsedRealtime() - mScreenOffTime;
        if (silenceAfter > 0
                && mScreenOffTime > 0
                && offTime > silenceAfter) {
            Slog.i(TAG, "screen off too long (" + offTime + "ms, limit " + silenceAfter
                    + "ms): not waking up the user with low battery sound");
            return;
        }

        if (DEBUG) {
            Slog.d(TAG, "playing low battery sound. pick-a-doop!"); // WOMP-WOMP is deprecated
        }

        if (Settings.Global.getInt(cr, Settings.Global.POWER_SOUNDS_ENABLED, 1) == 1) {
            final String soundPath = Settings.Global.getString(cr,
                    Settings.Global.LOW_BATTERY_SOUND);
            if (soundPath != null) {
                final Uri soundUri = Uri.parse("file://" + soundPath);
                if (soundUri != null) {
                    final Ringtone sfx = RingtoneManager.getRingtone(mContext, soundUri);
                    if (sfx != null) {
                        sfx.setStreamType(AudioManager.STREAM_SYSTEM);
                        sfx.play();
                    }
                }
            }
        }
    }

    void dismissInvalidChargerDialog() {
        if (mInvalidChargerDialog != null) {
            mInvalidChargerDialog.dismiss();
        }
    }

    void showInvalidChargerDialog() {
        Slog.d(TAG, "showing invalid charger dialog");

        dismissLowBatteryWarning();

        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
            b.setCancelable(true);
            b.setMessage(R.string.invalid_charger);
            b.setIconAttribute(android.R.attr.alertDialogIcon);
            b.setPositiveButton(android.R.string.ok, null);

        AlertDialog d = b.create();
            d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        mInvalidChargerDialog = null;
                        mBatteryLevelTextView = null;
                    }
                });

        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();
        mInvalidChargerDialog = d;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mLowBatteryAlertCloseLevel=");
        pw.println(mLowBatteryAlertCloseLevel);
        pw.print("mLowBatteryReminderLevels=");
        pw.println(Arrays.toString(mLowBatteryReminderLevels));
        pw.print("mInvalidChargerDialog=");
        pw.println(mInvalidChargerDialog == null ? "null" : mInvalidChargerDialog.toString());
        pw.print("mLowBatteryDialog=");
        pw.println(mLowBatteryDialog == null ? "null" : mLowBatteryDialog.toString());
        pw.print("mBatteryLevel=");
        pw.println(Integer.toString(mBatteryLevel));
        pw.print("mBatteryStatus=");
        pw.println(Integer.toString(mBatteryStatus));
        pw.print("mPlugType=");
        pw.println(Integer.toString(mPlugType));
        pw.print("mInvalidCharger=");
        pw.println(Integer.toString(mInvalidCharger));
        pw.print("mScreenOffTime=");
        pw.print(mScreenOffTime);
        if (mScreenOffTime >= 0) {
            pw.print(" (");
            pw.print(SystemClock.elapsedRealtime() - mScreenOffTime);
            pw.print(" ago)");
        }
        pw.println();
        pw.print("soundTimeout=");
        pw.println(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0));
        pw.print("bucket: ");
        pw.println(Integer.toString(findBatteryLevelBucket(mBatteryLevel)));
    }
}

