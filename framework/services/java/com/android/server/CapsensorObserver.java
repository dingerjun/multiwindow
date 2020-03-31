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
 * <p>CapsensorObserver monitors for a capsensor.
 */
final class CapsensorObserver extends UEventObserver {
    private static final String TAG = CapsensorObserver.class.getSimpleName();

    private static final String CAPSENSOR_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/capsensor";
    private static final String CAPSENSOR_STATE_PATH = "/sys/class/switch/capsensor/state";

    private static final int MSG_CAPSENSOR_STATE_CHANGED = 0;

    private final Object mLock = new Object();

    private int mCapsensorState = Intent.EXTRA_CAPSENSOR_STATE_FAR;
    private int mPreviousCapsensorState = Intent.EXTRA_CAPSENSOR_STATE_FAR;

    private boolean mSystemReady;

    private final Context mContext;
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    public CapsensorObserver(Context context) {
        mContext = context;

        mPowerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        init();  // set initial status
        startObserving(CAPSENSOR_UEVENT_MATCH);
    }

    @Override
    public void onUEvent(UEventObserver.UEvent event) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Slog.v(TAG, "capsensor UEVENT: " + event.toString());
        }

        synchronized (mLock) {
            try {
                int newState = Integer.parseInt(event.get("SWITCH_STATE"));
                if (newState != mCapsensorState) {
                    mPreviousCapsensorState = mCapsensorState;
                    mCapsensorState = newState;
                    if (mSystemReady) {
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
                FileReader file = new FileReader(CAPSENSOR_STATE_PATH);
                try {
                    int len = file.read(buffer, 0, 1024);
                    mCapsensorState = Integer.valueOf((new String(buffer, 0, len)).trim());
                    mPreviousCapsensorState = mCapsensorState;
                } finally {
                    file.close();
                }
            } catch (FileNotFoundException e) {
                Slog.w(TAG, "This kernel does not have capsensor support");
            } catch (Exception e) {
                Slog.e(TAG, "" , e);
            }
        }
    }

    void systemReady() {
        synchronized (mLock) {
            // don't bother broadcasting msg here
            if (mCapsensorState != Intent.EXTRA_CAPSENSOR_STATE_FAR) {
                updateLocked();
            }
            mSystemReady = true;
        }
    }

    private void updateLocked() {
        mWakeLock.acquire();
        mHandler.sendEmptyMessage(MSG_CAPSENSOR_STATE_CHANGED);
    }

    private void handleCapsensorStateChange() {
        synchronized (mLock) {
            Slog.i(TAG, "capsensor state changed: " + mCapsensorState);

            // Skip the intent if not yet provisioned.
            /***
            final ContentResolver cr = mContext.getContentResolver();
            if (Settings.Global.getInt(cr,
                    Settings.Global.DEVICE_PROVISIONED, 0) == 0) {
                Slog.i(TAG, "Device not provisioned, skipping capsensor broadcast");
                return;
            }
            ***/

            // Pack up the values and broadcast them to everyone
            Intent intent = new Intent(Intent.ACTION_CAPSENSOR_EVENT);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra(Intent.EXTRA_CAPSENSOR_STATE, mCapsensorState);

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
                case MSG_CAPSENSOR_STATE_CHANGED:
                    handleCapsensorStateChange();
                    break;
            }
        }
    };
}
