
package com.android.systemui.statusbar.policy;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;

public class ScreenOffTimeoutController {

    @SuppressWarnings("unused")
    private static final String TAG = "StatusBar.ScreenOffTimeoutController";

    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private Context mContext;
    private Integer mTimeout;

    private ArrayList<TimeoutChangeCallback> mChangeCallbacks = new ArrayList<TimeoutChangeCallback>();

    public interface TimeoutChangeCallback {
        public void onScreenOffTimeoutChanged(int timeout);
    }

    public ScreenOffTimeoutController(Context context) {
        mContext = context;
        //modiy lenovo-sw gengse1 2014/07/14 BLADEFHDKK-864 begin
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        List<UserInfo> users = userManager.getUsers();
        for (UserInfo user : users) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
                    true, mTimeoutChangeObserver, user.id);
        }//modiy lenovo-sw gengse1 2014/07/14 BLADEFHDKK-864 end
        refreshTimeout();
        //add lenovo-sw gengse1 2014/06/30 BLADEFHDKK-723 begin
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mReceiver, filter);
        //add lenovo-sw gengse1 2014/06/30 BLADEFHDKK-723 end
    }
    //add lenovo-sw gengse1 2014/06/30 BLADEFHDKK-723 begin
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
            if (action.equals(Intent.ACTION_USER_ADDED)) {
                mContext.getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
                        true, mTimeoutChangeObserver, userId);
            } else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                synchronized (ScreenOffTimeoutController.this) {
                    refreshTimeout();
                    for (TimeoutChangeCallback cb : mChangeCallbacks) {
                        cb.onScreenOffTimeoutChanged(mTimeout);
                    }
                }
            }
        }
    };//add lenovo-sw gengse1 2014/06/30 BLADEFHDKK-723 end

    private synchronized void refreshTimeout() {
        mTimeout = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
    }

    public void addStateChangedCallback(TimeoutChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public synchronized void toggleTimeout(int timeout) {
        mTimeout = timeout;
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                mTimeout);
    }

    private ContentObserver mTimeoutChangeObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            synchronized (ScreenOffTimeoutController.this) {
                refreshTimeout();
                for (TimeoutChangeCallback cb : mChangeCallbacks) {
                    cb.onScreenOffTimeoutChanged(mTimeout);
                }
            }
        }
    };

    public int getScreenOffTimeout() {
        return mTimeout;
    }

}
