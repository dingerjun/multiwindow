/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.recent;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarPanel;

import java.util.List;

/*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
import android.content.res.Configuration;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.lang.ref.WeakReference;
import android.os.Handler;
import com.android.systemui.statusbar.policy.Prefs;
import com.android.systemui.lenovo.usrguide.usrguideActivity;
import android.os.Message;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.graphics.drawable.Drawable;
/*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */

public class RecentsActivity extends Activity {
    private static final boolean DEBUG = true;
    public static final String TAG = "RecentsActivity";
    public static final String TOGGLE_RECENTS_INTENT = "com.android.systemui.recent.action.TOGGLE_RECENTS";
    public static final String PRELOAD_INTENT = "com.android.systemui.recent.action.PRELOAD";
    public static final String CANCEL_PRELOAD_INTENT = "com.android.systemui.recent.CANCEL_PRELOAD";
    public static final String CLOSE_RECENTS_INTENT = "com.android.systemui.recent.action.CLOSE";
    public static final String WINDOW_ANIMATION_START_INTENT = "com.android.systemui.recent.action.WINDOW_ANIMATION_START";
    public static final String PRELOAD_PERMISSION = "com.android.systemui.recent.permission.PRELOAD";
    public static final String WAITING_FOR_WINDOW_ANIMATION_PARAM = "com.android.systemui.recent.WAITING_FOR_WINDOW_ANIMATION";
    private static final String WAS_SHOWING = "was_showing";

    private RecentsPanelView mRecentsPanel;
    private IntentFilter mIntentFilter;
    private boolean mShowing;
    private boolean mForeground;

    /*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
    //View dummyMainView = null;
    
    ImageView subPanelView[] = {null,null,
            null,null};
    ImageView subForbidView = null;
    View subLayoutPanelView[] = {null,null};
    View viewSpacing[] = {null,null,null};
    private View mRecentsPanelIndicator;
    TextView subPromptView = null;
    
    private ImageButton mAllAppSwitch;
    private View mAppLauncher;
    private View mRecentAppList;
    static boolean isRecentAppList = false;
    private View mStatusBarRecentsLayout;
    private View mStatusBarRecentsBottom;
    /*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CLOSE_RECENTS_INTENT.equals(intent.getAction())) {
                if (mRecentsPanel != null && mRecentsPanel.isShowing()) {
                    if (mShowing && !mForeground) {
                        // Captures the case right before we transition to another activity
                        mRecentsPanel.show(false);
                    }
                }
            } else if (WINDOW_ANIMATION_START_INTENT.equals(intent.getAction())) {
                if (mRecentsPanel != null) {
                    mRecentsPanel.onWindowAnimationStart();
                }
            }
        }
    };
  
    public class TouchAllAppSwitchListener implements View.OnTouchListener {        
        private ImageButton mImgBtn;

        public TouchAllAppSwitchListener(ImageButton mImgBtn0) {
            mImgBtn = mImgBtn0;
        }
        
        public boolean onTouch(View v, MotionEvent ev) {
            final int action = ev.getAction();
            if(mImgBtn != null){
                switch(action){
                case MotionEvent.ACTION_DOWN:
                    vibrate();
                    if(isRecentAppList)
                    {
                        mImgBtn.setBackgroundResource(R.drawable.btn_recent_pressed);
                        isRecentAppList = false;
                        mAppLauncher.setVisibility(View.VISIBLE);
                        mRecentAppList.setVisibility(View.INVISIBLE);
                    }else
                    {
                        mImgBtn.setBackgroundResource(R.drawable.btn_recent_normal);
                        isRecentAppList = true;
                        mAppLauncher.setVisibility(View.INVISIBLE);
                        mRecentAppList.setVisibility(View.VISIBLE);
                    }
                    
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                    
                default:
                    break;
                }
                return true;
            }
            return false;
        }
    }
    
    
    public class TouchOutsideListener implements View.OnTouchListener {
        private StatusBarPanel mPanel;

        public TouchOutsideListener(StatusBarPanel panel) {
            mPanel = panel;
        }
        public boolean onTouch(View v, MotionEvent ev) {
            final int action = ev.getAction();
            if (action == MotionEvent.ACTION_OUTSIDE
                    || (action == MotionEvent.ACTION_DOWN
                    && !mPanel.isInContentArea((int) ev.getX(), (int) ev.getY()))) {
                dismissAndGoHome();
                return true;
            }
            return false;
        }
    }

/*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        if ((action == MotionEvent.ACTION_DOWN
                && isNotInPreviewArea((int) event.getX(), (int) event.getY()))) {
            dismissAndGoBack();
            return true;
        }
        return false;
    }

    public void vibrate() {
        android.os.Vibrator vib = (android.os.Vibrator)this.getSystemService(
                Context.VIBRATOR_SERVICE);
        vib.vibrate(50);
    }
    
    private boolean isNotInPreviewArea(int x,int y) {
        return mStatusBarRecentsBottom != null ? pointInsides(x, y,  mStatusBarRecentsBottom) : false;
    }

    private boolean pointInsides(int x, int y, View v2) {
        final int t = v2.getTop();
        // System.out.println("t : " + t + " b : " + b + " x : " + x + " y : " + y);
        return y < t;
    }
    
    private boolean isInContentArea(int x,int y) {
        return mStatusBarRecentsBottom != null ? pointInside(x, y, mStatusBarRecentsBottom) : false;
    }

    private boolean pointInside(int x, int y, View v) {
        final int l = v.getLeft();
        final int r = v.getRight();
        final int t = v.getTop();
        final int b = v.getBottom();
        // System.out.println("t : " + t + " b : " + b + " x : " + x + " y : " + y);
        return x >= l && x < r && y >= t && y < b;
    }
/*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */    
    
    @Override
    public void onPause() {
        overridePendingTransition(
                R.anim.recents_return_to_launcher_enter,
                R.anim.recents_return_to_launcher_exit);
        mForeground = false;
        if (mRecentsPanel != null) {
            mRecentsPanel.dismissContextMenuIfAny();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        mShowing = false;
        if (mRecentsPanel != null) {
            mRecentsPanel.onUiHidden();
            mRecentsPanel.show(false);
        }
        super.onStop();
    }

    private void updateWallpaperVisibility(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
    }

    public static boolean forceOpaqueBackground(Context context) {
        return WallpaperManager.getInstance(context).getWallpaperInfo() != null;
    }

    @Override
    public void onStart() {
    /*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
        // Hide wallpaper if it's not a static image
        //if (forceOpaqueBackground(this)) {

        /*if(mStatusBarRecentsBottom != null) {
            Animation animation = AnimationUtils.loadAnimation(this, R.anim.lenovo_status_bar_recent_bottom_enter_animation);
            animation.reset();
            mStatusBarRecentsBottom.setAnimation(animation);
        }*/

        if(mStatusBarRecentsLayout != null) {
            Animation animation = AnimationUtils.loadAnimation(this, R.anim.lenovo_status_bar_recent_enter_animation);
            animation.reset();
            mStatusBarRecentsLayout.setAnimation(animation);
        }

        updateWallpaperVisibility(false);
   /*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
        mShowing = true;
        if (mRecentsPanel != null) {
            // Call and refresh the recent tasks list in case we didn't preload tasks
            // or in case we don't get an onNewIntent
            mRecentsPanel.refreshRecentTasksList();
            mRecentsPanel.refreshViews();
        }
        super.onStart();
    }

    @Override
    public void onResume() {
        mForeground = true;
        /*if(mStatusBarRecentsLayout != null) {
            mStatusBarRecentsLayout.setVisibility(View.VISIBLE);
        }*/
        mAppLauncher.setVisibility(View.INVISIBLE);
        mRecentAppList.setVisibility(View.VISIBLE);
        isRecentAppList = true;
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        dismissAndGoBack();
    }

    public void dismissAndGoHome() {
        if (mRecentsPanel != null) {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivityAsUser(homeIntent, new UserHandle(UserHandle.USER_CURRENT));
            mRecentsPanel.show(false);
        }
    }

    public void dismissAndGoBack() {
        if (mRecentsPanel != null) {
            final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            final List<ActivityManager.RecentTaskInfo> recentTasks =
                    am.getRecentTasks(2,
                            ActivityManager.RECENT_WITH_EXCLUDED |
                            ActivityManager.RECENT_IGNORE_UNAVAILABLE);
            if (recentTasks.size() > 1 &&
                    mRecentsPanel.simulateClick(recentTasks.get(1).persistentId)) {
                // recents panel will take care of calling show(false) through simulateClick
                return;
            }
            mRecentsPanel.show(false);
        }
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
        setContentView(R.layout.lenovo_status_bar_recent_panel);
        mStatusBarRecentsLayout = findViewById(R.id.status_bar_recents_layout);
        mStatusBarRecentsBottom = findViewById(R.id.status_bar_recents_bottom);
        /*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */        
        mRecentsPanel = (RecentsPanelView) findViewById(R.id.recents_root);
        mRecentsPanel.setOnTouchListener(new TouchOutsideListener(mRecentsPanel));
        //mRecentsPanel.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        /*Begin,Lenovo-sw liming11 add 2013-12-16, add for the feature of MultiWindow */
        mRecentsPanelIndicator = findViewById(R.id.recents_panel_indicator);
        //dummyMainView = findViewById(R.id.dummy_main_view);

        /*Begin,Lenovo-sw liming11 add 2014-01-08, modify for MultiWindow */
        subPanelView[0] = (ImageView)findViewById(R.id.sub_panel_1);
        subPanelView[1] = (ImageView)findViewById(R.id.sub_panel_2);
        subPanelView[2] = (ImageView)findViewById(R.id.sub_panel_3);
        subPanelView[3] = (ImageView)findViewById(R.id.sub_panel_4);
        subForbidView = (ImageView)findViewById(R.id.sub_forbid_panel);

        viewSpacing[0] = (View)findViewById(R.id.spacing_0);
        viewSpacing[1] = (View)findViewById(R.id.spacing_1);
        viewSpacing[2] = (View)findViewById(R.id.spacing_2);
        subLayoutPanelView[0] = findViewById(R.id.sub_layout_panel_1);
        subLayoutPanelView[1] = findViewById(R.id.sub_layout_panel_2);
        
        /*End,Lenovo-sw liming11 add 2014-01-08, modify for MultiWindow */
        subPromptView = (TextView)findViewById(R.id.prompt_sub_0);
        //subPromptView.setText(R.id.prompt_info);
        mAllAppSwitch = (ImageButton) findViewById(R.id.prompt_sub_1);
        mAppLauncher = findViewById(R.id.all_apps_launcher);
        mRecentAppList = findViewById(R.id.recents_bg_protect);
        mAllAppSwitch.setOnTouchListener(new TouchAllAppSwitchListener(mAllAppSwitch));
        
        mRecentsPanel.mRecentsPanelIndicator = mRecentsPanelIndicator;
        //mRecentsPanel.dummyMainView = dummyMainView;
        for(int i=0; i<viewSpacing.length; i++){
            mRecentsPanel.viewSpacing[i] = viewSpacing[i];
        }
        
        for(int i=0; i<subPanelView.length; i++){
            mRecentsPanel.subPanelView[i] = subPanelView[i];
            mRecentsPanel.subPanelView[i].setScaleType(ScaleType.FIT_XY);
        }

        mRecentsPanel.subForbidView = subForbidView;
        mRecentsPanel.subForbidView.setScaleType(ScaleType.FIT_XY);

        for(int i=0; i<subLayoutPanelView.length; i++){
            mRecentsPanel.subLayoutPanelView[i] = subLayoutPanelView[i];
        }
        mRecentsPanel.subPromptView = subPromptView;
        /*Begin,Lenovo-sw liming11 add 2014-01-08, modify for MultiWindow */
        //mHandler = new MyHandler(this);
        /*End,Lenovo-sw Tom_liming11 add 2013-12-16, add for the feature of MultiWindow */
        
        final RecentTasksLoader recentTasksLoader = RecentTasksLoader.getInstance(this);
        recentTasksLoader.setRecentsPanel(mRecentsPanel, mRecentsPanel);
        mRecentsPanel.setMinSwipeAlpha(
                getResources().getInteger(R.integer.config_recent_item_min_alpha) / 100f);

        if (savedInstanceState == null ||
                savedInstanceState.getBoolean(WAS_SHOWING)) {
            handleIntent(getIntent(), (savedInstanceState == null));
        }
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(CLOSE_RECENTS_INTENT);
        mIntentFilter.addAction(WINDOW_ANIMATION_START_INTENT);
        registerReceiver(mIntentReceiver, mIntentFilter);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(WAS_SHOWING, mRecentsPanel.isShowing());
    }

    @Override
    protected void onDestroy() {
        RecentTasksLoader.getInstance(this).setRecentsPanel(null, mRecentsPanel);
        unregisterReceiver(mIntentReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent, true);
    }

    private void handleIntent(Intent intent, boolean checkWaitingForAnimationParam) {
        super.onNewIntent(intent);

        if (TOGGLE_RECENTS_INTENT.equals(intent.getAction())) {
            if (mRecentsPanel != null) {
                if (mRecentsPanel.isShowing()) {
                    dismissAndGoBack();
                } else {
                    final RecentTasksLoader recentTasksLoader = RecentTasksLoader.getInstance(this);
                    boolean waitingForWindowAnimation = checkWaitingForAnimationParam &&
                            intent.getBooleanExtra(WAITING_FOR_WINDOW_ANIMATION_PARAM, false);
                    mRecentsPanel.show(true, recentTasksLoader.getLoadedTasks(),
                            recentTasksLoader.isFirstScreenful(), waitingForWindowAnimation);
                }
            }
        }
    }

    boolean isForeground() {
        return mForeground;
    }

    boolean isActivityShowing() {
         return mShowing;
    }
    
    
    /* Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add the feature of usrguide for SystemUI about MultiWindow */
    private static final int WHAT_SHOW_POP = 1;
    private Handler mHandler;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) {
            checkAndShowRecentGuide();
        }
    }

    //private usrguideActivity mPopupWindow;
    private static final String RECENTS_USE_GUIDE_STATE = "recents_use_guide10";

    private void checkAndShowRecentGuide() {
        final boolean isShow = Prefs.read(this).getBoolean(RECENTS_USE_GUIDE_STATE, false);
    }
    /* End,Lenovo-sw Tom_liming11 add 2013-12-17, add the feature of usrguide for SystemUI about MultiWindow */   
}
