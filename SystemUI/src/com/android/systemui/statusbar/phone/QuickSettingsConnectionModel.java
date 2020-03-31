package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;
import com.android.systemui.R;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import com.android.systemui.settings.CurrentUserTracker;

/**
 * M: Support "Quick settings".
 */
public final class QuickSettingsConnectionModel {
    private static final String TAG = "QuickSettingsConnectionModel";
    private static final boolean DBG = true;
    private boolean mUpdating = false;
    private static final float FULL_BRIGHTNESS_THRESHOLD = 0.8f;
    private static final int COUNT = 5;
    private static final int WHITE = 0xffffffff;
    private static final int GREEN = 0xff99cc00;
    private Context mContext;
    private PhoneStatusBar mStatusBarService;

    /*added by weiyl2 2014-3-19*/
    private View mSmartSideBarModeTileView;
    private View mTimeoutTileView;
    /*added by weiyl2 2014-3-19*/

    //weiyl2
    private ImageView mSmartSideBarIcon;
    private ImageView mStandardIcon;
    private ImageView mVideoIcon;
    private ImageView mReadIcon;
    private AlertDialog mSwitchDialog;
    private Dialog mProfileSwitchDialog;
    private Handler mHandler = new Handler();

    private static final String SMARTSIDE_MODE_ACTION_CHANGED = "action.lenovo.scene_changed";
    private static final String SMARTSIDE_MODE_ACTION_CHANGINGTO = "action.lenovo.scene_changingto";
    public static final String MODE_VALUE = "scene_value";
    public static final int MODE_NORMAL = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_READER = 2;

    private static final String SCENE_FILE_NAME = "scene_file_name";
    private static final String SCENE_KEY = "scene_key";
    public static final int SCENE_NORMAL = 0;

  //weiyl2
    private Dialog mSmartSideModeSwitchDialog;
    private Notification mSmartSideBarNotification = new Notification();
    private int mNotificationID = 0;
    private NotificationManager mNotificationManager;
    private Intent mSmartSideBarIntent = new Intent();
    private CharSequence title,message;
    int currentValue = -1;
    private TextView mStandardText;
    private TextView mVideoText;
    private TextView mReadText;
  //weiyl2
    private static final int SMART_SIDE_SWITCH_DIALOG_LONG_TIMEOUT = 4000;
    public static  SharedPreferences userInfo;
    public  static SharedPreferences.Editor editor;
    private final CurrentUserTracker mUserTracker;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                Log.d(TAG, "onReceive called, action is " + action);
            }
             if(action.equals(SMARTSIDE_MODE_ACTION_CHANGED)){
                int mode = intent.getIntExtra(MODE_VALUE,0);
                updateSmartSideBarView(mode);
                sendNotificationForSmartBar(mode);
                setSmartSideBarScene(mContext,currentValue);
             }
        }
    };

    public QuickSettingsConnectionModel(Context context) {
        mContext = context;
        userInfo = context.getSharedPreferences(SCENE_FILE_NAME, Context.MODE_WORLD_WRITEABLE);
        editor = userInfo.edit();
        mUserTracker = new CurrentUserTracker(mContext){
            @Override
            public void onUserSwitched(int newUserId) {
            }
       };
    }

    public static int getSmartSideBarScene(){
        int scene = userInfo.getInt(SCENE_KEY, SCENE_NORMAL);
        return scene;
    }

    public static void setSmartSideBarScene(Context context, int value){
        editor.putInt(SCENE_KEY, value);
        editor.commit();
    }

    public void buildIconViews() {
        currentValue = QuickSettingsConnectionModel.getSmartSideBarScene();
        mSmartSideBarModeTileView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mUserTracker.getCurrentUserId() == 0){
                    showSmartSideBarSwitchDialog();
                }
            }
        });
        
        createSmartSideSwitchDialog();
        setSmartSideBarScene(mContext,currentValue);
    }

    private AlertDialog createDialog(View v, int resId) {
        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
        b.setCancelable(true).setTitle(resId).setView(v, 0, 0, 0, 0)
                .setInverseBackgroundForced(true).setNegativeButton(
                        android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (mSwitchDialog != null) {
                                    mSwitchDialog.hide();
                                }
                            }
                        });
        AlertDialog alertDialog = b.create();
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        return alertDialog;
    }

    public void dismissDialogs() {
        if (mSwitchDialog != null) {
            mSwitchDialog.dismiss();
        }
    }

    public void setUpdates(boolean update) {
        if (update != mUpdating) {
            mUpdating = update;
            if (update) {
                IntentFilter filter = new IntentFilter();
                /// M: for mobile config

                filter.addAction(SMARTSIDE_MODE_ACTION_CHANGED);
                mContext.registerReceiver(mIntentReceiver, filter);
            } else {
                mContext.unregisterReceiver(mIntentReceiver);
            }
        }
    }

    public void setStatusBarService(PhoneStatusBar statusBarService) {
        mStatusBarService = statusBarService;
    }

    public void updateResources() {
      //weiyl2
        TextView smartSideBarText = (TextView)mSmartSideBarModeTileView.findViewById(R.id.smart_side_bar_textview);
        smartSideBarText.setText(R.string.scene_title);

        boolean isSmartSideBarSwitchDialogVisible = false;
        if (mSmartSideModeSwitchDialog != null) {
            removeAllSmartSideBarModeSwitchDialogCallbacks();

            isSmartSideBarSwitchDialogVisible = mSmartSideModeSwitchDialog.isShowing();
            mSmartSideModeSwitchDialog.dismiss();
        }
        mSmartSideModeSwitchDialog = null;
        if (isSmartSideBarSwitchDialogVisible) {
            showSmartSideBarSwitchDialog();
        }
    }

    public void addSmartSideBarTile(View smartSideBarModeTileView) {
        mSmartSideBarModeTileView = smartSideBarModeTileView;
        Log.d("weiyl2", "addSmartSideBarTile");
        mSmartSideBarIcon = (ImageView) mSmartSideBarModeTileView.findViewById(R.id.smart_side_bar);
    }

  //weiyl2

    private View.OnClickListener mSmartSideBarSwitchListener = new View.OnClickListener() {
     public void onClick(View v) {
         int key = (Integer)v.getTag();
         sendSmartSideBarModechangeBroadcast(key);
         sendNotificationForSmartBar(key);
         updateSmartSideBarView(key);
         setSmartSideBarScene(mContext,currentValue);
         if(mSmartSideModeSwitchDialog != null){
            mSmartSideModeSwitchDialog.dismiss();
         }
     }
 };

 private void updateSmartSideBarView(int ssm) {
     loadDisabledSmartSideBarResouceForAll();
     loadEnabledSmartSideBarResource(ssm);
 }

 private void loadDisabledSmartSideBarResouceForAll() {
     mStandardIcon.setImageResource(R.drawable.standard);
     mVideoIcon.setImageResource(R.drawable.play);
     mReadIcon.setImageResource(R.drawable.read);
 }

 private void loadEnabledSmartSideBarResource(int ssm) {
     if (DBG) {
         Log.d(TAG, "loadEnabledSmartSideBarResource called, SmartSideBar is: " + ssm);
     }
     currentValue = ssm;
     switch (ssm) {
     case MODE_NORMAL:
         mStandardIcon.setImageResource(R.drawable.standard_click);
         mStandardText.setTextColor(GREEN);
         mVideoText.setTextColor(WHITE);
         mReadText.setTextColor(WHITE);
         mSmartSideBarIcon.setImageResource(R.drawable.sstandard_on);
         break;
     case MODE_VIDEO:
         mVideoIcon.setImageResource(R.drawable.play_click);
         mVideoText.setTextColor(GREEN);
         mStandardText.setTextColor(WHITE);
         mReadText.setTextColor(WHITE);
         mSmartSideBarIcon.setImageResource(R.drawable.splay_on);
         break;
     case MODE_READER:
         mReadIcon.setImageResource(R.drawable.read_click);
         mReadText.setTextColor(GREEN);
         mVideoText.setTextColor(WHITE);
         mStandardText.setTextColor(WHITE);
         mSmartSideBarIcon.setImageResource(R.drawable.sread_on);
         break;
     default:
         mSmartSideBarIcon.setImageResource(R.drawable.sstandard_on);
         break;
     }
 }

 private void showSmartSideBarSwitchDialog() {
     createSmartSideSwitchDialog();
     if (!mSmartSideModeSwitchDialog.isShowing()) {
         try {
             WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
         } catch (RemoteException e) {
         }
         mSmartSideModeSwitchDialog.show();
         dismissSmartSideBarSwitchDialog(SMART_SIDE_SWITCH_DIALOG_LONG_TIMEOUT);
     }
 }

 private void createSmartSideSwitchDialog() {
     if (mSmartSideModeSwitchDialog == null) {
         mSmartSideModeSwitchDialog = new Dialog(mContext);
         mSmartSideModeSwitchDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
         mSmartSideModeSwitchDialog.setContentView(R.layout.quick_settings_smart_side_bar_mode_switch);
         mSmartSideModeSwitchDialog.setCanceledOnTouchOutside(true);
         mSmartSideModeSwitchDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
         mSmartSideModeSwitchDialog.getWindow().getAttributes().privateFlags |=
                 WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
         mSmartSideModeSwitchDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

         mStandardIcon = (ImageView) mSmartSideModeSwitchDialog.findViewById(R.id.standard_mode_icon);
         mStandardText = (TextView)mSmartSideModeSwitchDialog.findViewById(R.id.normal_text);
         mVideoIcon = (ImageView) mSmartSideModeSwitchDialog.findViewById(R.id.video_mode_icon);
         mVideoText = (TextView)mSmartSideModeSwitchDialog.findViewById(R.id.video_text);
         mReadIcon = (ImageView) mSmartSideModeSwitchDialog.findViewById(R.id.read_mode_icon);
         mReadText = (TextView)mSmartSideModeSwitchDialog.findViewById(R.id.read_text);

         View standardMode = (View) mSmartSideModeSwitchDialog.findViewById(R.id.standard_mode);
         standardMode.setOnClickListener(mSmartSideBarSwitchListener);
         standardMode.setTag(MODE_NORMAL);

         View videoMode = (View) mSmartSideModeSwitchDialog.findViewById(R.id.video_mode);
         videoMode.setOnClickListener(mSmartSideBarSwitchListener);
         videoMode.setTag(MODE_VIDEO);

         View readMode = (View) mSmartSideModeSwitchDialog.findViewById(R.id.read_mode);
         readMode.setOnClickListener(mSmartSideBarSwitchListener);
         readMode.setTag(MODE_READER);
         if(currentValue >= 0){
            Log.d(TAG,"currentValue is " + currentValue);
            loadEnabledSmartSideBarResource(currentValue);
        }
    }
 }
 private void dismissSmartSideBarSwitchDialog(int timeout) {
     removeAllSmartSideBarModeSwitchDialogCallbacks();
     if (mSmartSideModeSwitchDialog != null) {
         mHandler.postDelayed(mDismissSmartSideBarSwitchDialogRunnable, timeout);
     }
 }

 private Runnable mDismissSmartSideBarSwitchDialogRunnable = new Runnable() {
     public void run() {
         if (mSmartSideModeSwitchDialog != null && mSmartSideModeSwitchDialog.isShowing()) {
             mSmartSideModeSwitchDialog.dismiss();
         }
          removeAllSmartSideBarModeSwitchDialogCallbacks();
     };
 };

 private void removeAllSmartSideBarModeSwitchDialogCallbacks() {
     mHandler.removeCallbacks(mDismissSmartSideBarSwitchDialogRunnable);
 }

 public void sendSmartSideBarModechangeBroadcast(int mode){
     Intent intent = new Intent(SMARTSIDE_MODE_ACTION_CHANGINGTO);
     Log.d(TAG,"sendSmartSideBarModechangeBroadcast: action is " + intent);
     intent.putExtra(MODE_VALUE,mode);
     mContext.sendBroadcast(intent);
 }

 private  void setSmartRotation(String value){
     String mIsAllowToCheck = SystemProperties.get("persist.sys.rotate_change","1");
     if("1".equals(mIsAllowToCheck)){
         SystemProperties.set("persist.sys.smart_rotate", value);
     }
 }

 public void sendNotificationForSmartBar(int mode){
     Log.d(TAG,"sendNotificationForSmartBar: mode is " + mode);
     mNotificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
     mNotificationManager.cancel(mNotificationID);
     switch(mode){
     case MODE_NORMAL:
     mNotificationID = R.string.scene_normal;
     mSmartSideBarNotification.icon = R.drawable.normal;
     setSmartRotation("1");
     break;
     case MODE_VIDEO:
     mNotificationID = R.string.scene_video;
     mSmartSideBarNotification.icon = R.drawable.watch;
     setSmartRotation("0");
     break;
    case MODE_READER:
     mNotificationID = R.string.scene_reader;
     mSmartSideBarNotification.icon = R.drawable.reader;
     setSmartRotation("1");
     break;
     }

     mSmartSideBarNotification.when = 0;
     mSmartSideBarNotification.flags = Notification.FLAG_ONGOING_EVENT;
     mSmartSideBarNotification.defaults = 0;
     mSmartSideBarNotification.sound = null;
     mSmartSideBarNotification.vibrate = null;
     title = mContext.getResources().getText(R.string.scene_title);
     message = mContext.getResources().getText(mNotificationID);

     PendingIntent pi = PendingIntent.getActivity(mContext,0,mSmartSideBarIntent,0);
     mSmartSideBarNotification.setLatestEventInfo(mContext,title,message,pi);
     mNotificationManager.notify(mNotificationID,mSmartSideBarNotification);
 }
}
