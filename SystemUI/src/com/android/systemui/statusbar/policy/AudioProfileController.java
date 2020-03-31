
package com.android.systemui.statusbar.policy;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

public class AudioProfileController {

    @SuppressWarnings("unused")
    private static final String TAG = "StatusBar.AudioProfileController";

    private ArrayList<AudioProfileChangeCallback> mChangeCallbacks = new ArrayList<AudioProfileChangeCallback>();

    private Context mContext;

    private String mAudioProfileName;

    public AudioProfileController(Context context) {
        mContext = context;
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.PROFILE_TYPE), true,
                mAudioProfileChangeObserver);
        refreshAudioProfile();
    }

    private void refreshAudioProfile() {
        mAudioProfileName = Settings.System.getString(
                mContext.getContentResolver(), Settings.System.PROFILE_TYPE);
        if (mAudioProfileName == null) {
            mAudioProfileName = Settings.System.PROFILE_GENERAL;
        }
    }

    public void addStateChangedCallbck(AudioProfileChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public String getAudioProfileName() {
        return mAudioProfileName;
    }

    public interface AudioProfileChangeCallback {
        public void onAudioProfileChanged(String profileName);
    }

    private ContentObserver mAudioProfileChangeObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            synchronized (AudioProfileController.this) {
                refreshAudioProfile();
                for (AudioProfileChangeCallback cb : mChangeCallbacks) {
                    cb.onAudioProfileChanged(mAudioProfileName);
                }
            }
        }
    };

    public void setAudioProfile(String audioProfileName) {
        if (audioProfileName == null || audioProfileName.equals(mAudioProfileName)) {
            return;
        }
        Intent intent = new Intent("android.intent.action.PROFILE_CHANGED");
        intent.putExtra("from", this.mAudioProfileName);
        intent.putExtra("to", audioProfileName);
        intent.putExtra("module", "SystemUI");
        this.mContext.sendBroadcast(intent);
    }

}
