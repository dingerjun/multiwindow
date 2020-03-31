package com.android.server;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;

import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * <p>HallObserver monitors for a hall sensor.
 */
final class HallObserver extends UEventObserver {
    private static final String TAG = HallObserver.class.getSimpleName();

    private static final String HALL_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/hall";
    private static final String HALL_STATE_PATH = "/sys/class/switch/hall/state";

    private static final int MSG_HALL_STATE_CHANGED = 0;

    private final Object mLock = new Object();

    private int mHallState = Intent.EXTRA_HALL_STATE_FAR;
    private int mPreviousHallState = Intent.EXTRA_HALL_STATE_FAR;

    private boolean mSystemReady;

    private final Context mContext;
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    public HallObserver(Context context) {
        mContext = context;

        mPowerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        init();  // set initial status
        startObserving(HALL_UEVENT_MATCH);
    }

    @Override
    public void onUEvent(UEventObserver.UEvent event) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Slog.v(TAG, "Hall UEVENT: " + event.toString());
        }

        synchronized (mLock) {
            try {
                int newState = Integer.parseInt(event.get("SWITCH_STATE"));
                if (newState != mHallState) {
                    mPreviousHallState = mHallState;
                    mHallState = newState;
                    if (mSystemReady) {
                        // Wake up immediately when magnetic object removed.
                        if (mHallState == Intent.EXTRA_HALL_STATE_FAR)
                        {
                            Slog.i(TAG, "Magnetic object removed, Wake up immediately");
                            mPowerManager.wakeUp(SystemClock.uptimeMillis());
                        }

                        updateLocked();
                    }
                }
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Could not parse switch state from event " + event);
            }
        }
    }

    private void init() {
        synchronized (mLock) {
            try {
                char[] buffer = new char[1024];
                FileReader file = new FileReader(HALL_STATE_PATH);
                try {
                    int len = file.read(buffer, 0, 1024);
                    mHallState = Integer.valueOf((new String(buffer, 0, len)).trim());
                    mPreviousHallState = mHallState;
                } finally {
                    file.close();
                }
            } catch (FileNotFoundException e) {
                Slog.w(TAG, "This kernel does not have hall sensor support");
            } catch (Exception e) {
                Slog.e(TAG, "" , e);
            }
        }
    }

    void systemReady() {
        synchronized (mLock) {
            // don't bother broadcasting msg here
            if (mHallState != Intent.EXTRA_HALL_STATE_FAR) {
                updateLocked();
            }
            mSystemReady = true;
        }
    }

    private void updateLocked() {
        mWakeLock.acquire();
        mHandler.sendEmptyMessage(MSG_HALL_STATE_CHANGED);
    }

    private void handleHallStateChange() {
        synchronized (mLock) {
            Slog.i(TAG, "Hall sensor state changed: " + mHallState);

            // Skip the intent if not yet provisioned.
            /***
            final ContentResolver cr = mContext.getContentResolver();
            if (Settings.Global.getInt(cr,
                    Settings.Global.DEVICE_PROVISIONED, 0) == 0) {
                Slog.i(TAG, "Device not provisioned, skipping hall sensor broadcast");
                return;
            }
            ***/

            // Pack up the values and broadcast them to everyone
            Intent intent = new Intent(Intent.ACTION_HALL_EVENT);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra(Intent.EXTRA_HALL_STATE, mHallState);

            // Send the event intent.
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

            // Release the wake lock that was acquired when the message was posted.
            mWakeLock.release();
        }
    }

    private final Handler mHandler = new Handler(true /*async*/) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_HALL_STATE_CHANGED:
                    handleHallStateChange();
                    break;
            }
        }
    };
}
