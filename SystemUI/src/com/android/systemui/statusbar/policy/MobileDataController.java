
package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.provider.Settings;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import java.util.ArrayList;

public class MobileDataController {

    private Context mContext;
    private ConnectivityManager mConnectivityManager;
    private ArrayList<MobileDataStateChangeCallback> mChangeCallbacks = new ArrayList<MobileDataStateChangeCallback>();

    public MobileDataController(Context context) {
        mContext = context;
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                true, mMobileDataStateChangeObserver);
        MobileDataControllerBroadcastReceiver rcv = new MobileDataControllerBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(rcv, filter);
    }
    private class MobileDataControllerBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)
                    || IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)
                    || IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                    // Disable mobile data when sim not available.
                    notifiyListeners(false);
                } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                    notifiyListeners(isMobileDataEnabled());
                }
            }else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
                if(tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                   notifiyListeners(isMobileDataEnabled());
                } else {
                    notifiyListeners(false);
                }
            }
        }
    }
    public void toggleMobileData() {
        ContentResolver cr = mContext.getContentResolver();
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        //enable toggle mobile data when airplane mode is off and SIM card is ready
        if ((Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 0) &&
            (tm.getSimState() == TelephonyManager.SIM_STATE_READY)) {
            mConnectivityManager.setMobileDataEnabled(!mConnectivityManager.getMobileDataEnabled());
        }
    }

    public boolean isMobileDataEnabled() {
        ContentResolver cr = mContext.getContentResolver();
        if (Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 0)
            return mConnectivityManager.getMobileDataEnabled();
        else
            return false;
    }

    public void addStateChangedCallbck(MobileDataStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    private ContentObserver mMobileDataStateChangeObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            ContentResolver cr = mContext.getContentResolver();
            if (Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 0) {
                boolean enabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MOBILE_DATA, 0) == 1;
                notifiyListeners(enabled);
            } else {
                notifiyListeners(false);
            }
        }
    };

    private void notifiyListeners(boolean enabled) {
        for (MobileDataStateChangeCallback cb : this.mChangeCallbacks) {
            cb.onMobileDataStateChanged(enabled);
        }
    }

    public interface MobileDataStateChangeCallback {
        void onMobileDataStateChanged(boolean enabled);
    }

}
