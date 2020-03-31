
package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.dolby.DsClient;
import android.dolby.DsConstants;
import android.dolby.IDsClientEvents;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

public class DolbyController extends BroadcastReceiver {

    private static final String TAG = "StatusBar.DolbyController";
    private static final boolean DEBUG = true;

    private ArrayList<DolbyStateChangeCallback> mChangeCallbacks = new ArrayList<DolbyStateChangeCallback>();
    private Context mContext;
    private DsClient mDolbyServiceClient;
    private boolean mDolbyServiceConnected;
    private boolean mIsDolbyEnabled;
    private Handler mHandler = new MyHandler();

    public DolbyController(Context context) {
        mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(this, filter);
        mHandler.sendEmptyMessageDelayed(MyHandler.MSG_CONNECT_REQUEST,
                MyHandler.DELAY_MILLISECONDS);
        // mHandler.sendEmptyMessage(MyHandler.MSG_CONNECT_REQUEST);
    }

    public void addStateChangedCallbck(DolbyStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            mHandler.sendEmptyMessage(MyHandler.MSG_CONNECT_REQUEST);
            if (DEBUG)
                Log.d(TAG, "unregister receiver");
            mContext.unregisterReceiver(this);
        }
    }

    private synchronized void connectDolbyService() {
        if (DEBUG)
            Log.d(TAG, "connectDolbyService()");
        if (this.mDolbyServiceConnected) {
            return;
        }
        if (this.mDolbyServiceClient == null) {
            this.mDolbyServiceClient = new DsClient();
            IDsClientEvents listener = new IDsClientEvents() {
                @Override
                public void onClientConnected() {
                    handleConnectedEvent();
                }

                @Override
                public void onClientDisconnected() {
                    handleDisconnectedEvent();
                }

                @Override
                public void onDsOn(boolean on) {
                    handleStateChange(on);
                }

                @Override
                public void onEqSettingsChanged(int profile, int preset) {
                }

                @Override
                public void onProfileNameChanged(int profile, String name) {
                }

                @Override
                public void onProfileSelected(int profile) {
                }

                @Override
                public void onProfileSettingsChanged(int profile) {
                }
            };
            mDolbyServiceClient.setEventListener(listener);
        }
        this.mDolbyServiceClient.bindDsService(mContext);
        this.mHandler.sendEmptyMessageDelayed(MyHandler.MSG_CONNECT_REQUEST,
                MyHandler.DELAY_MILLISECONDS);
    }

    private synchronized void handleConnectedEvent() {
        if (DEBUG)
            Log.d(TAG, "handleConnectedEvent()");
        this.mDolbyServiceConnected = true;
        try {
            this.mIsDolbyEnabled = this.mDolbyServiceClient.getDsOn();
            this.notifyListeners(mIsDolbyEnabled);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void notifyListeners(boolean enabled) {
        for (DolbyStateChangeCallback cb : mChangeCallbacks) {
            cb.onDolbyStateChanged(enabled);
        }
    }

    private synchronized void handleDisconnectedEvent() {
        if (DEBUG)
            Log.d(TAG, "handleDisconnectedEvent()");
        this.mDolbyServiceConnected = false;
        this.mHandler.sendEmptyMessageDelayed(MyHandler.MSG_CONNECT_REQUEST,
                MyHandler.DELAY_MILLISECONDS);
    }

    private synchronized void handleStateChange(boolean on) {
        if (DEBUG)
            Log.d(TAG, "handleStateChange(" + on + ")");
        this.mIsDolbyEnabled = on;
        this.notifyListeners(mIsDolbyEnabled);
    }

    public synchronized void toggleDolby() {
        if (DEBUG)
            Log.d(TAG, "toggleDolby()");
        if (mDolbyServiceConnected) {
            try {
                int result = mDolbyServiceClient.setDsOnChecked(!mIsDolbyEnabled);
                if (result == DsConstants.DS_NO_ERROR) {
                    mIsDolbyEnabled = !mIsDolbyEnabled;
                    notifyListeners(mIsDolbyEnabled);
                } else {
                    Log.e(TAG, "fail to change dolby state to " + !mIsDolbyEnabled);
                }
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        } else {
            mHandler.sendEmptyMessage(MyHandler.MSG_CONNECT_REQUEST);
        }
    }

    public synchronized boolean isDolbyEnabled() {
        if (DEBUG)
            Log.d(TAG, "isDolbyEnabled()");
        return mIsDolbyEnabled;
    }

    public interface DolbyStateChangeCallback {
        public void onDolbyStateChanged(boolean enabled);
    }

    private class MyHandler extends Handler {
        private static final int MSG_CONNECT_REQUEST = 0;
        private static final long DELAY_MILLISECONDS = 60 * 1000;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECT_REQUEST:
                    this.removeMessages(MSG_CONNECT_REQUEST);
                    connectDolbyService();
                    break;
            }
        }
    }

}
