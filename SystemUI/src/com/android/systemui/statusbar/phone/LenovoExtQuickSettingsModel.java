
package com.android.systemui.statusbar.phone;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.AudioProfileController;
import com.android.systemui.statusbar.policy.AudioProfileController.AudioProfileChangeCallback;
import com.android.systemui.statusbar.policy.DolbyController;
import com.android.systemui.statusbar.policy.DolbyController.DolbyStateChangeCallback;
import com.android.systemui.statusbar.policy.MobileDataController;
import com.android.systemui.statusbar.policy.MobileDataController.MobileDataStateChangeCallback;
import com.android.systemui.statusbar.policy.ScreenOffTimeoutController;
import com.android.systemui.statusbar.policy.ScreenOffTimeoutController.TimeoutChangeCallback;
import com.android.systemui.settings.CurrentUserTracker;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

public class LenovoExtQuickSettingsModel extends QuickSettingsModel
        implements TimeoutChangeCallback, AudioProfileChangeCallback, DolbyStateChangeCallback,
        MobileDataStateChangeCallback {

    private static final int AUDIO_PROFILE_DIALOG_LONG_TIMEOUT = 4000;

    private QuickSettingsTileView mScreenOffTimeoutTile;
    private RefreshCallback mScreenOffTimeoutCallback;
    private ScreenOffTimeoutState mScreenOffTimeoutState = new ScreenOffTimeoutState();
    private ScreenOffTimeoutController mScreenOffTimeoutController;

    private QuickSettingsBasicTile mAudioProfileTile;
    private RefreshCallback mAudioProfileCallback;
    private AudioProfileState mAudioProfileState = new AudioProfileState();
    private AudioProfileController mAudioProfileController;
    private Dialog mAudioProfileDialog;
    private CurrentUserTracker mCurrentUserTracker;
    private Handler mHandler = new Handler();
    private OnClickListener mAudioProfileListener = new View.OnClickListener() {
        public void onClick(View v) {
            String profileKey = (String) v.getTag();
            if (profileKey != null) {
                mAudioProfileController.setAudioProfile(profileKey);
                mAudioProfileDialog.dismiss();
            }
        }
    };
    private Runnable mDismissAudioProfileDialogRunnable = new Runnable() {
        public void run() {
            if (mAudioProfileDialog != null && mAudioProfileDialog.isShowing()) {
                mAudioProfileDialog.dismiss();
            }
            removeAllAudioProfileDialogCallbacks();
        };
    };

    private QuickSettingsTileView mDolbyTile;
    private RefreshCallback mDolbyCallback;
    private State mDolbyState = new State();
    private DolbyController mDolbyController;

    private QuickSettingsTileView mMobileDataTile;
    private RefreshCallback mMobileDataCallback;
    private State mMobileDataState = new State();
    private MobileDataController mMobileDataController;

    static class ScreenOffTimeoutState extends State {
        String description;
    }

    static class AudioProfileState extends State {
        String description;
    }

    public LenovoExtQuickSettingsModel(Context context) {
        super(context);
        mScreenOffTimeoutController = new ScreenOffTimeoutController(context);
        mScreenOffTimeoutController.addStateChangedCallback(this);
        mAudioProfileController = new AudioProfileController(context);
        mAudioProfileController.addStateChangedCallbck(this);
        mDolbyController = new DolbyController(context);
        mDolbyController.addStateChangedCallbck(this);
        mMobileDataController = new MobileDataController(context);
        mMobileDataController.addStateChangedCallbck(this);
        mCurrentUserTracker = new CurrentUserTracker(context) {
            @Override
            public void onUserSwitched(int newUserId) {
            }
        };
    }

    public static class ScreenOffTimeoutInfo {
        public final int mMilliseconds;
        public final int mIconId;
        public final int mDescription;

        public ScreenOffTimeoutInfo(int milliseconds, int iconId, int description) {
            this.mMilliseconds = milliseconds;
            this.mIconId = iconId;
            this.mDescription = description;
        }
    }

    private static List<ScreenOffTimeoutInfo> sScreenOffTimeoutList = new ArrayList<ScreenOffTimeoutInfo>();
    private static TreeMap<Integer, ScreenOffTimeoutInfo> sScreenOffTimeoutMap = new TreeMap<Integer, ScreenOffTimeoutInfo>();
    private Integer mScreenOffTimeout;

    static {
        sScreenOffTimeoutList.add(new ScreenOffTimeoutInfo(30000, R.drawable.ic_qs_sleep_30s,
                R.string.screen_off_timeout_30s_description));
        sScreenOffTimeoutList.add(new ScreenOffTimeoutInfo(60000, R.drawable.ic_qs_sleep_1m,
                R.string.screen_off_timeout_1m_description));
        sScreenOffTimeoutList.add(new ScreenOffTimeoutInfo(300000, R.drawable.ic_qs_sleep_5m,
                R.string.screen_off_timeout_5m_description));
        for (ScreenOffTimeoutInfo timeout : sScreenOffTimeoutList) {
            sScreenOffTimeoutMap.put(timeout.mMilliseconds, timeout);
        }
    }

    void addScreenOffTimeoutTile(QuickSettingsBasicTile view, RefreshCallback cb) {
        mScreenOffTimeoutTile = view;
        mScreenOffTimeoutTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Integer next = sScreenOffTimeoutMap.higherKey(mScreenOffTimeout);
                if (next != null) {
                    mScreenOffTimeout = next;
                } else {
                    mScreenOffTimeout = sScreenOffTimeoutMap.firstKey();
                }
                mScreenOffTimeoutController.toggleTimeout(mScreenOffTimeout);
            }
        });
        mScreenOffTimeoutCallback = cb;
        onScreenOffTimeoutChanged(mScreenOffTimeoutController.getScreenOffTimeout());
    }

    @Override
    public void onScreenOffTimeoutChanged(int timeout) {
        if (mScreenOffTimeoutCallback == null) {
            return;
        }
        mScreenOffTimeout = null;
        Iterator<Integer> iterator = sScreenOffTimeoutMap.keySet().iterator();
        while (iterator.hasNext()) {
            int key = iterator.next();
            if (timeout <= key) {
                mScreenOffTimeout = key;
                break;
            }
        }
        if (mScreenOffTimeout == null) {
            mScreenOffTimeout = sScreenOffTimeoutMap.lastKey();
        }
        ScreenOffTimeoutInfo timeoutInfo = sScreenOffTimeoutMap.get(mScreenOffTimeout);
        mScreenOffTimeoutState.iconId = timeoutInfo.mIconId;
        mScreenOffTimeoutState.label = mContext.getResources().getString(
                R.string.quick_settings_screen_off_timeout_label);
        mScreenOffTimeoutState.description = mContext.getResources().getString(
                timeoutInfo.mDescription);
        mScreenOffTimeoutCallback.refreshView(mScreenOffTimeoutTile,
                mScreenOffTimeoutState);
    }

    public static class AudioProfile {
        public final String mLabel;
        public final int mQuickSettingIconId;
        public final int mDescriptionId;
        public final int mSelectedIconId;
        public final int mUnselectedIconId;
        public boolean mInUse;

        public AudioProfile(String label, int quickSettingIconId,
                int descriptionId, int selectedIconId, int unselectedIconId) {
            this.mLabel = label;
            this.mQuickSettingIconId = quickSettingIconId;
            this.mDescriptionId = descriptionId;
            this.mSelectedIconId = selectedIconId;
            this.mUnselectedIconId = unselectedIconId;
        }
    }

    private static List<AudioProfile> sAudioProfileList = new ArrayList<AudioProfile>();
    private static TreeMap<String, AudioProfile> sAudioProfileMap = new TreeMap<String, AudioProfile>();

    static {
        sAudioProfileList.add(new AudioProfile(Settings.System.PROFILE_GENERAL,
                R.drawable.ic_qs_general_on,
                R.string.audio_profil_general_description,
                R.drawable.ic_qs_general_profile_on,
                R.drawable.ic_qs_general_profile_off));
        sAudioProfileList.add(new AudioProfile(Settings.System.PROFILE_SILENCE,
                R.drawable.ic_qs_silence_on,
                R.string.audio_profil_silence_description,
                R.drawable.ic_qs_silence_profile_on,
                R.drawable.ic_qs_silence_profile_off));
        sAudioProfileList.add(new AudioProfile(Settings.System.PROFILE_MEETING,
                R.drawable.ic_qs_meeting_on,
                R.string.audio_profile_vibration_description,
                R.drawable.ic_qs_meeting_profile_on,
                R.drawable.ic_qs_meeting_profile_off));
        sAudioProfileList.add(new AudioProfile(
                Settings.System.PROFILE_OUTDOORS, R.drawable.ic_qs_outdoor_on,
                R.string.audio_profil_outdoor_description,
                R.drawable.ic_qs_outdoor_profile_on,
                R.drawable.ic_qs_outdoor_profile_off));
        for (AudioProfile profile : sAudioProfileList) {
            sAudioProfileMap.put(profile.mLabel, profile);
        }
    }

    void addAudioProfileTile(QuickSettingsBasicTile view, RefreshCallback cb) {
        mAudioProfileTile = view;
        mAudioProfileTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentUserTracker.getCurrentUserId() == 0) {
                    showAudioProfileDialog();
                }
            }
        });
        mAudioProfileCallback = cb;
        onAudioProfileChanged(mAudioProfileController.getAudioProfileName());
    }

    private void showAudioProfileDialog() {
        createAudioProfileDialog();
        updateProfileUi();
        if (!mAudioProfileDialog.isShowing()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
            } catch (RemoteException e) {
            }
            mAudioProfileDialog.show();
            dismissAudioProfileDialog(AUDIO_PROFILE_DIALOG_LONG_TIMEOUT);
        }
    }

    private void createAudioProfileDialog() {
        if (mAudioProfileDialog == null) {
            mAudioProfileDialog = new Dialog(mContext);
            mAudioProfileDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mAudioProfileDialog
                    .setContentView(R.layout.quick_settings_audio_profile_dialog);
            mAudioProfileDialog.setCanceledOnTouchOutside(true);
            mAudioProfileDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
            mAudioProfileDialog.getWindow().getAttributes().privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mAudioProfileDialog.getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            ImageView image1 = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_1_icon);
            image1.setOnClickListener(mAudioProfileListener);
            ImageView image2 = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_2_icon);
            image2.setOnClickListener(mAudioProfileListener);
            ImageView image3 = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_3_icon);
            image3.setOnClickListener(mAudioProfileListener);
            ImageView image4 = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_4_icon);
            image4.setOnClickListener(mAudioProfileListener);
        }
    }

    private void updateProfileUi() {
        if (mAudioProfileDialog != null) {
            int i = 1;
            for (AudioProfile profile : sAudioProfileList) {
                ImageView image = null;
                TextView text = null;
                switch (i++) {
                    case 1:
                        image = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_1_icon);
                        text = (TextView) mAudioProfileDialog.findViewById(R.id.profile_1_text);
                        break;
                    case 2:
                        image = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_2_icon);
                        text = (TextView) mAudioProfileDialog.findViewById(R.id.profile_2_text);
                        break;
                    case 3:
                        image = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_3_icon);
                        text = (TextView) mAudioProfileDialog.findViewById(R.id.profile_3_text);
                        break;
                    case 4:
                        image = (ImageView) mAudioProfileDialog.findViewById(R.id.profile_4_icon);
                        text = (TextView) mAudioProfileDialog.findViewById(R.id.profile_4_text);
                        break;
                }
                if (image != null && text != null) {
                    image.setImageResource(profile.mInUse ? profile.mSelectedIconId
                            : profile.mUnselectedIconId);
                    text.setText(profile.mDescriptionId);
                    image.setTag(profile.mLabel);
                }
            }
        }
    }

    private void dismissAudioProfileDialog(int timeout) {
        removeAllAudioProfileDialogCallbacks();
        if (mAudioProfileDialog != null) {
            mHandler.postDelayed(mDismissAudioProfileDialogRunnable, timeout);
        }
    }

    private void removeAllAudioProfileDialogCallbacks() {
        mHandler.removeCallbacks(mDismissAudioProfileDialogRunnable);
    }

    @Override
    public void onAudioProfileChanged(String profileName) {
        if (mAudioProfileCallback == null) {
            return;
        }

        for (AudioProfile profile : sAudioProfileList) {
            if (profile.mLabel.equals(profileName)) {
                profile.mInUse = true;
            } else {
                profile.mInUse = false;
            }
        }
        AudioProfile audioProfile = sAudioProfileMap.get(profileName);
        mAudioProfileState.iconId = audioProfile.mQuickSettingIconId;
        mAudioProfileState.label = mContext.getResources().getString(
                R.string.quick_settings_audio_profile_label);
        mAudioProfileState.description = mContext.getResources().getString(
                audioProfile.mDescriptionId);
        mAudioProfileCallback
                .refreshView(mAudioProfileTile, mAudioProfileState);
    }

    void addDolbyTile(QuickSettingsBasicTile view, RefreshCallback cb) {
        mDolbyTile = view;
        mDolbyTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDolbyController.toggleDolby();
            }
        });
        mDolbyCallback = cb;
        onDolbyStateChanged(mDolbyController.isDolbyEnabled());
    }

    @Override
    public void onDolbyStateChanged(boolean enabled) {
        if (mDolbyCallback == null) {
            return;
        }
        mDolbyState.enabled = enabled;
        mDolbyState.iconId = enabled ? R.drawable.ic_qs_dolby_on : R.drawable.ic_qs_dolby_off;
        mDolbyState.label = mContext.getResources().getString(R.string.quick_settings_dolby_label);
        mDolbyCallback.refreshView(mDolbyTile, mDolbyState);
    }

    void addMobileDataTile(QuickSettingsBasicTile view, RefreshCallback cb) {
        this.mMobileDataTile = view;
        this.mMobileDataTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMobileDataController.toggleMobileData();
            }
        });
        mMobileDataCallback = cb;
        onMobileDataStateChanged(mMobileDataController.isMobileDataEnabled());
    }

    @Override
    public void onMobileDataStateChanged(boolean enabled) {
        if (mMobileDataCallback == null) {
            return;
        }
        mMobileDataState.enabled = enabled;
        mMobileDataState.iconId = enabled ? R.drawable.ic_qs_mobile_data_on
                : R.drawable.ic_qs_mobile_data_off;
        mMobileDataState.label = mContext.getResources().getString(
                R.string.quick_settings_mobile_data_label);
        mMobileDataCallback.refreshView(mMobileDataTile, mMobileDataState);
    }

    // add by xutg1, BLADEFHD-903, update resources when system language change
    void updateResources() {
        super.updateResources();
        refreshMobileDataTile();
        refreshScreenOffTimeoutTile();
        refreshAudioProfileTile();
        refreshDolbyTile();
    }

    void refreshMobileDataTile() {
        if (mMobileDataCallback == null) {
            return;
        }
        mMobileDataState.label = mContext.getResources().getString(
            R.string.quick_settings_mobile_data_label);
        mMobileDataCallback.refreshView(mMobileDataTile, mMobileDataState);
    }

    void refreshScreenOffTimeoutTile() {
        if (mScreenOffTimeoutCallback == null) {
            return;
        }
        mScreenOffTimeoutState.label = mContext.getResources().getString(
            R.string.quick_settings_screen_off_timeout_label);
        mScreenOffTimeoutCallback.refreshView(mScreenOffTimeoutTile,
                                              mScreenOffTimeoutState);
    }

    void refreshAudioProfileTile() {
        if (mAudioProfileCallback == null) {
            return;
        }
        mAudioProfileState.label = mContext.getResources().getString(
            R.string.quick_settings_audio_profile_label);        
        mAudioProfileCallback
            .refreshView(mAudioProfileTile, mAudioProfileState);
    }

    void refreshDolbyTile() {
        if (mDolbyCallback == null) {
            return;
        }
        mDolbyState.label = mContext.getResources().getString(R.string.quick_settings_dolby_label);
        mDolbyCallback.refreshView(mDolbyTile, mDolbyState);
    }
    // add end, BLADEFHD-903
}
