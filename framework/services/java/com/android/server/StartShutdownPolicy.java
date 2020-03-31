package com.android.server;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.R;

class StartShutdownPolicy {
    private static final String TAG = "StartShutdownService";
    private final static String AUTO_START_ACTION = "com.android.startshutdown.AUTO_START";
    private final static String AUTO_SHUTDOWN_ACTION = "com.android.startshutdown.AUTO_SHUTDOWN";
    private final static String AUTO_START_TIME_HOUR = "startshutdown.autostarthour";
    private final static String AUTO_START_TIME_MINUTE = "startshutdown.autostartminute";
    private final static String AUTO_SHUTDOWN_TIME_HOUR = "startshutdown.autoshutdownhour";
    private final static String AUTO_SHUTDOWN_TIME_MINUTE = "startshutdown.autoshutdownminute";
    private static final int MESSAGE_SHUTDOWN_CONFIRM = 2;

    private static final int SHUTDOWN_TIME_DELAY = 30000;
    private final static int INVALID_TIME = 0xFF;

    private final Context mContext;
    private final Object mCallStateSync = new Object();
    private final StartShutdownService.ServiceCallback mCallback;
    private int mStartHour = INVALID_TIME;
    private int mStartMinute = INVALID_TIME;
    private int mShutdownHour = INVALID_TIME;
    private int mShutdownMinute = INVALID_TIME;
    private AlertDialog mShutdownDialog = null;
    private final AlarmManager mAlarmManager;
    private boolean mReducedBoot;
    private int mDelay = SHUTDOWN_TIME_DELAY / 1000;
    private boolean mCalling=false;


    static final String AUTO_SHUTDOWN_NOTIFICATION_ACTION = "lenovo.intent.action.TIMELY_SHUTDOWN";

    public StartShutdownPolicy(Context context,
            StartShutdownService.ServiceCallback callback) {
        mContext = context;
        mCallback = callback;

        mStartHour = Settings.System.getInt(context.getContentResolver(),
                AUTO_START_TIME_HOUR, INVALID_TIME);
        mStartMinute = Settings.System.getInt(context.getContentResolver(),
                AUTO_START_TIME_MINUTE, INVALID_TIME);
        mShutdownHour = Settings.System.getInt(context.getContentResolver(),
                AUTO_SHUTDOWN_TIME_HOUR, INVALID_TIME);
        mShutdownMinute = Settings.System.getInt(context.getContentResolver(),
                AUTO_SHUTDOWN_TIME_MINUTE, INVALID_TIME);

        IntentFilter filter = new IntentFilter();
        filter.addAction(AUTO_START_ACTION);
        filter.addAction(AUTO_SHUTDOWN_ACTION);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(AUTO_SHUTDOWN_NOTIFICATION_ACTION);
        context.registerReceiver(mIntentReceiver, filter);

        mAlarmManager = (AlarmManager) mContext
                .getSystemService(Context.ALARM_SERVICE);
    }

    public void init(boolean reducedBoot) {
        mReducedBoot = reducedBoot;
        if (mReducedBoot) {
            initAutoStart();
        }
    }

    public void onBootCompleted() {
        if (!mReducedBoot) {
            initAutoStart();
        }
        initAutoShutdown();
    }

    private void initAutoStart() {
        if (isAutoStartTimeSet()) {
            enableAutoStartAlert(mStartHour, mStartMinute);
        }
    }

    private void initAutoShutdown() {
        if (isAutoShutdownTimeSet()) {
            enableAutoShutdownAlert(mShutdownHour, mShutdownMinute);
        }
    }

    public void enableAutoStart(int hour, int minute) {
        if (isAutoStartTimeSet()) {
            disableAutoStart();
        }
        if (setAutoStartTime(hour, minute)) {
            enableAutoStartAlert(hour, minute);
        }
    }

    private void enableAutoStartAlert(int hour, int minute) {
        long time = calculateAlarm(hour, minute).getTimeInMillis();

        Log.d(TAG, "\n***AutoStartShutdownPolicy --- EnableAutoStart at: "
                + time + "***\n");
        Intent intent = new Intent(AUTO_START_ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        mAlarmManager.set(AlarmManager.RTC_STARTUP, time, sender);
        SystemProperties.set(StartShutdownService.RTC_START_TIME_PROPERTY, Long.toString(time));
    }

    public void disableAutoStart() {
        if (isAutoStartTimeSet()) {
            setAutoStartTime(INVALID_TIME, INVALID_TIME);

            Log.d(TAG, "***AutoStartShutdownPolicy --- DisableAutoStart");
            Intent intent = new Intent(AUTO_START_ACTION);
            PendingIntent sender = PendingIntent.getBroadcast(mContext, 0,
                    intent, PendingIntent.FLAG_CANCEL_CURRENT);
            SystemProperties.set("persist.service.autostartenbale", "disable");
            mAlarmManager.cancel(sender);
            SystemProperties.set(StartShutdownService.RTC_START_TIME_PROPERTY, Long.toString(0));
        }
    }

    public void enableAutoShutdown(int hour, int minute) {
        if (isAutoShutdownTimeSet()) {
            disableAutoShutdown();
        }
        if (setAutoShutdownTime(hour, minute)) {
            enableAutoShutdownAlert(hour, minute);
        }
    }

    private void enableAutoShutdownAlert(int hour, int minute) {
        long time = calculateAlarm(hour, minute).getTimeInMillis();

        Log.d(TAG, "***AutoStartShutdownPolicy --- EnableAutoShutdown at: "
                + time + "***");
        Intent intent = new Intent(AUTO_SHUTDOWN_ACTION);
        intent.putExtra("Long_time", time);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, time, sender);
    }

    public void disableAutoShutdown() {
        if (isAutoShutdownTimeSet()) {
            setAutoShutdownTime(INVALID_TIME, INVALID_TIME);

            Log.d(TAG, "***AutoStartShutdownPolicy --- DisableAutoShutdown");
            Intent intent = new Intent(AUTO_SHUTDOWN_ACTION);
            PendingIntent sender = PendingIntent.getBroadcast(mContext, 0,
                    intent, PendingIntent.FLAG_CANCEL_CURRENT);
            mAlarmManager.cancel(sender);
        }
    }

    private boolean setAutoStartTime(int hour, int minute) {
        if ((hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59)
                || (hour == INVALID_TIME && minute == INVALID_TIME)) {
            mStartHour = hour;
            mStartMinute = minute;

            Settings.System.putInt(mContext.getContentResolver(),
                    AUTO_START_TIME_HOUR, hour);
            Settings.System.putInt(mContext.getContentResolver(),
                    AUTO_START_TIME_MINUTE, minute);
            return true;
        } else {
            return false;
        }
    }

    private boolean setAutoShutdownTime(int hour, int minute) {
        if ((hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59)
                || (hour == INVALID_TIME && minute == INVALID_TIME)) {
            mShutdownHour = hour;
            mShutdownMinute = minute;

            Settings.System.putInt(mContext.getContentResolver(),
                    AUTO_SHUTDOWN_TIME_HOUR, hour);
            Settings.System.putInt(mContext.getContentResolver(),
                    AUTO_SHUTDOWN_TIME_MINUTE, minute);
            return true;
        } else {
            return false;
        }
    }

    private Calendar calculateAlarm(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());

        int nowHour = c.get(Calendar.HOUR_OF_DAY);
        int nowMinute = c.get(Calendar.MINUTE);

        if (hour < nowHour || hour == nowHour && minute <= nowMinute) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public boolean isAutoStartTimeSet() {
        return (mStartHour != INVALID_TIME && mStartMinute != INVALID_TIME);
    }

    public boolean isAutoShutdownTimeSet() {
        return (mShutdownHour != INVALID_TIME && mShutdownMinute != INVALID_TIME);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AUTO_SHUTDOWN_ACTION)) {

                long setFor = intent.getLongExtra("Long_time", 0);
                long now = System.currentTimeMillis();
                if (now > setFor + 60 * 30 * 1000)
                    return;

                handleAutoShutdown();
            } else if (action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                if (isAutoStartTimeSet()) {
                    enableAutoStart(mStartHour, mStartMinute);
                }
                if (isAutoShutdownTimeSet()) {
                    enableAutoShutdown(mShutdownHour, mShutdownMinute);
                }

            } else if (mShutdownDialog != null
                    && Intent.ACTION_SCREEN_OFF.equals(action)) {
                releaseAfterScreenOffWakeLock(mContext);
                acquireAfterScreenOffWakeLock(mContext);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                releaseAfterScreenOffWakeLock(mContext);
            } else if (AUTO_SHUTDOWN_NOTIFICATION_ACTION.equals(action)) {
                handleAutoShutdown();
            }
        }
    };

    private final void cancelShutdown() {
        mHandler.removeMessages(MESSAGE_SHUTDOWN_CONFIRM);
        mShutdownDialog.dismiss();
        TelephonyManager tm = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mDelay = SHUTDOWN_TIME_DELAY / 1000;

        NotificationManager nm = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setAutoCancel(true);
        builder.setSmallIcon(com.android.internal.R.drawable.ic_timely_shutdown);
        builder.setTicker(mContext
                .getString(com.android.internal.R.string.auto_shutdown_interrupted));
        builder.setContentTitle(mContext
                .getString(com.android.internal.R.string.startup_auto_shutdown_title));
        builder.setContentText(mContext
                .getString(com.android.internal.R.string.auto_shutdown_interrupted));
        builder.setContentIntent(PendingIntent.getBroadcast(mContext, 0,
                new Intent(AUTO_SHUTDOWN_NOTIFICATION_ACTION),
                PendingIntent.FLAG_CANCEL_CURRENT));
        if (Settings.System.getInt(mContext.getContentResolver(), Settings.System.VIBRATE_IN_SILENT, 1) == 1) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }
        nm.notify(0, builder.getNotification());
        initAutoShutdown();
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            // TODO Auto-generated method stub
            synchronized (mCallStateSync) {
                Log.i(TAG, "call state changed: " + state);
                if (state != TelephonyManager.CALL_STATE_IDLE) {
                    Log.i(TAG, "call state is not idle, cancel shutdown");
                    mCalling=true;
                    cancelShutdown();
                }else{
                    Log.d(TAG,"call state:idle");
                    mCalling=false;
                }
                mCallStateSync.notifyAll();
            }
        }
    };
    private Ringtone mRingtone;
    private void handleAutoShutdown() {
        synchronized (mCallStateSync) {
            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_SHUTDOWN_CONFIRM));
            TelephonyManager tm = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            final ContentResolver cr = mContext.getContentResolver();
            String profile = Settings.System.getString(cr,
                    Settings.System.PROFILE_TYPE);
            if (profile == null) {
                profile = "";
            }

            try {
                mCallStateSync.wait(500);
            } catch (InterruptedException e) {
            }
            Log.d(TAG, "start to check profile and calling state");
            if ((profile.equals("general") || profile.equals("outdoors")) && !mCalling) {
                final Uri soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                if (soundUri != null) {
                    mRingtone = RingtoneManager.getRingtone(mContext,
                            soundUri);
                    if (mRingtone != null) {
                        mRingtone.setStreamType(AudioManager.STREAM_SYSTEM);
                        mRingtone.play();
                        Log.d(TAG, "play DEFAULT_ALARM_ALERT_URI");
                    }
                }
            }
        }
    }

    private void showAutoShutdownAlert() {
        String text = mContext.getString(
                R.string.startup_confirm_auto_shutdown, Long.toString(mDelay));

        if (mShutdownDialog == null) {
            mShutdownDialog = new AlertDialog.Builder(mContext)
                    .setTitle(com.android.internal.R.string.startup_auto_shutdown_title)
                    .setMessage(text)
                    .setPositiveButton(com.android.internal.R.string.power_off,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    mHandler.removeMessages(MESSAGE_SHUTDOWN_CONFIRM);
                                    mCallback.shutdownAction();
                                }
                            })
                    .setNegativeButton(com.android.internal.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    if (mRingtone != null) {
                                        mRingtone.stop();
                                    }
                                    mHandler.removeMessages(MESSAGE_SHUTDOWN_CONFIRM);
                                    TelephonyManager tm = (TelephonyManager) mContext
                                            .getSystemService(Context.TELEPHONY_SERVICE);
                                    tm.listen(mPhoneStateListener,
                                            PhoneStateListener.LISTEN_NONE);
                                }
                            }).create();
            mShutdownDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mShutdownDialog.setCancelable(false);
            mShutdownDialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            mShutdownDialog.setOnDismissListener(mShutdownDialogListener);
            mShutdownDialog.show();

        } else {
            mShutdownDialog.setMessage(text);
        }

    }

    private DialogInterface.OnDismissListener mShutdownDialogListener = new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface dialog) {
            releaseScreenWakeLock();
            mHandler.removeMessages(MESSAGE_SHUTDOWN_CONFIRM);
            mShutdownDialog = null;
            mDelay = SHUTDOWN_TIME_DELAY / 1000;
        }
    };

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_SHUTDOWN_CONFIRM:
                if (mDelay > 0) {
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MESSAGE_SHUTDOWN_CONFIRM),
                            1000);
                    acquireScreenWakeLock(mContext);
                    showAutoShutdownAlert();
                    mDelay--;
                } else {
                    if (mRingtone != null) {
                        mRingtone.stop();
                    }
                    mCallback.shutdownAction();
                    mShutdownDialog.dismiss();
                }
                break;
            }
        }
    };

    private PowerManager.WakeLock mScreenWakeLock = null;

    private PowerManager.WakeLock mAutoShutdownAfterScreenOffWakeLock = null;

    private void acquireAfterScreenOffWakeLock(Context context) {
        if (mAutoShutdownAfterScreenOffWakeLock != null)
            return;
        PowerManager pm = (PowerManager) context
                .getSystemService(Context.POWER_SERVICE);
        mAutoShutdownAfterScreenOffWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "AutoShutdownExtra");
        mAutoShutdownAfterScreenOffWakeLock.acquire();
    }

    private void releaseAfterScreenOffWakeLock(Context context) {
        if (mAutoShutdownAfterScreenOffWakeLock == null)
            return;
        mAutoShutdownAfterScreenOffWakeLock.release();
        mAutoShutdownAfterScreenOffWakeLock = null;
    }

    private void acquireScreenWakeLock(Context context) {
        if (mScreenWakeLock != null) {
            return;
        }

        PowerManager pm = (PowerManager) context
                .getSystemService(Context.POWER_SERVICE);
        mScreenWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "AutoShutdown");
        mScreenWakeLock.acquire();
    }

    private void releaseScreenWakeLock() {
        if (mScreenWakeLock != null) {
            mScreenWakeLock.release();
            mScreenWakeLock = null;
        }
    }

}
