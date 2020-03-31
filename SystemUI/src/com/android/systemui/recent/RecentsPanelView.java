/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewRootImpl;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarPanel;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.splitbar.StackBoxInfoUtils;
import com.android.systemui.splitbar.StackBoxInfoUtils.WinDisplayMode;

import java.util.ArrayList;

//MultiWindow NJ lenovo liming11 add 2013-12-06 begin
import android.content.ActivityNotFoundException;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.ActivityManager.StackBoxInfo;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.View.DragShadowBuilder;
import android.view.View.OnDragListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.LinearLayout;
import java.util.List;
import android.util.Slog;
import android.content.pm.ComponentInfo;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Display;
import android.os.Message;
//MultiWindow NJ lenovo liming11 add 2013-12-06 end
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.os.AsyncTask;
import android.graphics.drawable.LayerDrawable;

public class RecentsPanelView extends FrameLayout implements OnItemClickListener, RecentsCallback,
        StatusBarPanel, Animator.AnimatorListener, View.OnTouchListener {
    static final String TAG = "RecentsPanelView";
    static final boolean DEBUG = PhoneStatusBar.DEBUG || false;
    private PopupMenu mPopup;
    private View mRecentsScrim;
    private View mRecentsNoApps;
    private RecentsScrollView mRecentsContainer;

    private boolean mShowing;
    private boolean mWaitingToShow;
    private ViewHolder mItemToAnimateInWhenWindowAnimationIsFinished;
    private boolean mAnimateIconOfFirstTask;
    private boolean mWaitingForWindowAnimation;
    private long mWindowAnimationStartTime;
    private boolean mCallUiHiddenBeforeNextReload;

    private RecentTasksLoader mRecentTasksLoader;
    private ArrayList<TaskDescription> mRecentTaskDescriptions;
    private TaskDescriptionAdapter mListAdapter;
    private int mThumbnailWidth;
    private boolean mFitThumbnailToXY;
    private int mRecentItemLayoutId;
    private boolean mHighEndGfx;

/*Begin,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
    View mRecentsPanelIndicator;
    private GridView mAppsLauncherView0;
    private GridView mAppsLauncherView1;
    private View mResizerView;
    private int launcherItemSize = 0;
    private int mSeekerValue = 50;
    private List<ResolveInfo> mAppsSub0 = null;
    private List<ResolveInfo> mAppsSub1 = null;
/*End,Lenovo-sw Tom_liming11 add 2013-12-17, add for SystemUI about MultiWindow */
    private float mInitialTouchPosY;
    private float mInitialTouchPosX;
    private boolean inMultiWindowMode = false;
    private View templeView = null;

    //MultiWindow NJ lenovo liming11 add 2013-12-06 begin
    private List<ResolveInfo> mApps = null;
    private List<ResolveInfo> mAppsInWhiteList = null;
    private int mAppPaddingSpacing;
    private boolean isInWhiteList = false;

    private int loadApps(int screenWidth,int width, int spacing) {
        if(DEBUG) Log.v(TAG,"loadApps");

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        mApps = mContext.getPackageManager().queryIntentActivities(mainIntent,PackageManager.GET_RESOLVED_FILTER);

        mAppsInWhiteList = null;
        mAppsInWhiteList = new ArrayList<ResolveInfo>();
        /*Begin,Lenovo sw , Tom-liming11 2014-02-19 BLADEFHD-2395*/
        for (int i =0;i < mApps.size();i++) {
             if (appInWhiteList(mApps.get(i).activityInfo.packageName)) {
                 if ((mApps.get(i).activityInfo.packageName.equalsIgnoreCase("com.lenovo.ideafriend"))){
                     if(mApps.get(i).activityInfo.name.equalsIgnoreCase("com.lenovo.ideafriend.alias.MmsActivity"))
                         mAppsInWhiteList.add(mApps.get(i));
                 }else
                     mAppsInWhiteList.add(mApps.get(i));
             }
        }
        //del duplicate app
        delDupApps(mAppsInWhiteList);
        /*End,Lenovo sw, Tom-liming11 2014-02-19 BLADEFHD-2395*/

        return splitApps(screenWidth,width, spacing);
    }

    /*Begin,Lenovo sw, Tom-liming11 2014-02-19 BLADEFHD-2395*/
    private void delDupApps(List<ResolveInfo> arrayList){
        for(int i = 0; i < arrayList.size()-1; i++){
            for(int j = i+1; j < arrayList.size();){
                if(arrayList.get(i).activityInfo.packageName.equalsIgnoreCase(arrayList.get(j).activityInfo.packageName))
                    arrayList.remove(j);
                else
                    j++;
                }
            }
    }
    /*End,Lenovo sw, Tom-liming11 2014-02-19 BLADEFHD-2395*/

    private boolean appInWhiteList(String packageName) {
        Resources res = mContext.getResources();
        String[] whiteList = res.getStringArray(com.lenovo.internal.R.array.apps_white_list);
        if (whiteList == null) {
            return false;
        }
        for (int i = 0;i<whiteList.length;i++) {
             if (packageName.equalsIgnoreCase(whiteList[i])) {
                return true;
            }
        }
        return false;
    }

    /*Begin,Lenovo-sw Tom_liming11 modify the method of split apps for BLADEFHD-283 2014-02-10, */
    private int splitApps(int screenWidth,int width, int spacing){
        int mUseWidth = screenWidth - 2*mAppPaddingSpacing;
        int mBase = mAppsInWhiteList.size()/2;
//dingej1 ,2014.06.20 begin. BLADEFHDKK-144. GridView.getView Crash. multithread corner case.
        //mAppsSub0 = null;
        //mAppsSub1 = null;
//dingej1 end.
        if((mAppsInWhiteList.size() * (width+spacing) - 2*spacing) >= mUseWidth*2){
            if(0 == mAppsInWhiteList.size()%2) {
                mAppsSub0 = mAppsInWhiteList.subList(0, mBase);
                mAppsSub1 = mAppsInWhiteList.subList(mBase, mAppsInWhiteList.size());
            }else{
                mAppsSub0 = mAppsInWhiteList.subList(0, mBase+1);
                mAppsSub1 = mAppsInWhiteList.subList(mBase+1, mAppsInWhiteList.size());
            }
            if(DEBUG) Log.v("Tom","2 mBase == " + mBase + " mAppsInWhiteList.size() == " + mAppsInWhiteList.size());
            return 2;
        }else{
            //if(mUseWidth/(width+spacing))
            int mFirstSize = (mUseWidth/(width+spacing)+1);
            int mSecondSize = mAppsInWhiteList.size() - mFirstSize;
            if(mAppsInWhiteList.size()>= mFirstSize)
                mAppsSub0 = mAppsInWhiteList.subList(0, mFirstSize);
            else{
                mAppsSub0 = mAppsInWhiteList;
                if(DEBUG) Log.v("Tom","1 mFirstSize == " + mFirstSize + " mAppsInWhiteList.size() == " + mAppsInWhiteList.size());
            }
            if(mSecondSize > 0){
                mAppsSub1 = mAppsInWhiteList.subList(mFirstSize, mAppsInWhiteList.size());
                if(DEBUG) Log.v("Tom","2 mFirstSize == " + mFirstSize + " mAppsInWhiteList.size() == " + mAppsInWhiteList.size());
                return 2;
            }
            return 1;
        }
    }
    /*end,Lenovo-sw Tom_liming11 modify the method of split apps for BLADEFHD-283 2014-02-10, */
    
    public class AppsLauncherAdapterSub0 extends BaseAdapter {

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(mContext);
            if(mAppsSub0 != null){
                ResolveInfo info = mAppsSub0.get(position % mAppsSub0.size());
                i.setImageDrawable(info.activityInfo.loadIcon(mContext.getPackageManager()));
                i.setScaleType(ImageView.ScaleType.FIT_CENTER);
                final int w = launcherItemSize;
                i.setLayoutParams(new GridView.LayoutParams(w, w));
                return i;
            }
            return null;
        }

        public final int getCount() {
            return mAppsSub0 != null ? mAppsSub0.size():0;
        }

        public final Object getItem(int position) {
            return mAppsSub0 != null ? mAppsSub0.get(position % mAppsSub0.size()):null;
        }
 
        public final long getItemId(int position) {
            return position;
        }
    }
    
    public class AppsLauncherAdapterSub1 extends BaseAdapter {

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(mContext);
            if(mAppsSub1 != null){
                ResolveInfo info = mAppsSub1.get(position % mAppsSub1.size());
                i.setImageDrawable(info.activityInfo.loadIcon(mContext.getPackageManager()));
                i.setScaleType(ImageView.ScaleType.FIT_CENTER);
                final int w = launcherItemSize;
                i.setLayoutParams(new GridView.LayoutParams(w, w));
                return i;
            }
            return null;
        }

        public final int getCount() {
            return mAppsSub1 != null ? mAppsSub1.size():0;
        }

        public final Object getItem(int position) {
            return mAppsSub1 != null ? mAppsSub1.get(position % mAppsSub1.size()):null;
        }
 
        public final long getItemId(int position) {
            return position;
        }
    }
//MultiWindow NJ lenovo liming11 add 2013-12-06 end
    public static interface RecentsScrollView {
        public int numItemsInOneScreenful();
        public void setAdapter(TaskDescriptionAdapter adapter);
        public void setCallback(RecentsCallback callback);
        public void setMinSwipeAlpha(float minAlpha);
        public View findViewForTask(int persistentTaskId);
        public void drawFadedEdges(Canvas c, int left, int right, int top, int bottom);
        public void setOnScrollListener(Runnable listener);
    }

    private final class OnLongClickDelegate implements View.OnLongClickListener {
        View mOtherView;
        OnLongClickDelegate(View other) {
            mOtherView = other;
        }
        public boolean onLongClick(View v) {
            return mOtherView.performLongClick();
        }
    }

    /* package */ final static class ViewHolder {
        View thumbnailView;
        ImageView thumbnailViewImage;
        Drawable thumbnailViewDrawable;
        ImageView iconView;
        TextView labelView;
        TextView descriptionView;
        //View calloutLine;
        TaskDescription taskDescription;
        boolean loadedThumbnailAndIcon;
    }

    /* package */ final class TaskDescriptionAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public TaskDescriptionAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mRecentTaskDescriptions != null ? mRecentTaskDescriptions.size() : 0;
        }

        public Object getItem(int position) {
            return position; // we only need the index
        }

        public long getItemId(int position) {
            return position; // we just need something unique for this position
        }

        public View createView(ViewGroup parent) {
            View convertView = mInflater.inflate(mRecentItemLayoutId, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.thumbnailView = convertView.findViewById(R.id.app_thumbnail);
            holder.thumbnailViewImage =
                    (ImageView) convertView.findViewById(R.id.app_thumbnail_image);
            // If we set the default thumbnail now, we avoid an onLayout when we update
            // the thumbnail later (if they both have the same dimensions)
            updateThumbnail(holder, mRecentTasksLoader.getDefaultThumbnail(), false, false);
            holder.iconView = (ImageView) convertView.findViewById(R.id.app_icon);
            holder.iconView.setImageDrawable(mRecentTasksLoader.getDefaultIcon());
            holder.labelView = (TextView) convertView.findViewById(R.id.app_label);
            //holder.calloutLine = convertView.findViewById(R.id.recents_callout_line);
            holder.descriptionView = (TextView) convertView.findViewById(R.id.app_description);

            convertView.setTag(holder);
            return convertView;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = createView(parent);
            }
            final ViewHolder holder = (ViewHolder) convertView.getTag();

            // index is reverse since most recent appears at the bottom...
            final int index = mRecentTaskDescriptions.size() - position - 1;

            final TaskDescription td = mRecentTaskDescriptions.get(index);

            holder.labelView.setText(td.getLabel());
            holder.thumbnailView.setContentDescription(td.getLabel());
            holder.loadedThumbnailAndIcon = td.isLoaded();
            if (td.isLoaded()) {
                updateThumbnail(holder, td.getThumbnail(), true, false);
                updateIcon(holder, td.getIcon(), true, false);
            }
            if (index == 0) {
                if (mAnimateIconOfFirstTask) {
                    ViewHolder oldHolder = mItemToAnimateInWhenWindowAnimationIsFinished;
                    if (oldHolder != null) {
                        oldHolder.iconView.setAlpha(1f);
                        oldHolder.iconView.setTranslationX(0f);
                        oldHolder.iconView.setTranslationY(0f);
                        oldHolder.labelView.setAlpha(1f);
                        oldHolder.labelView.setTranslationX(0f);
                        oldHolder.labelView.setTranslationY(0f);
                    }
                    mItemToAnimateInWhenWindowAnimationIsFinished = holder;
                    int translation = -getResources().getDimensionPixelSize(
                            R.dimen.status_bar_recents_app_icon_translate_distance);
                    final Configuration config = getResources().getConfiguration();
                    if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                            translation = -translation;
                        }
                        holder.iconView.setAlpha(0f);
                        holder.iconView.setTranslationX(translation);
                        holder.labelView.setAlpha(0f);
                        holder.labelView.setTranslationX(translation);
                        /*holder.calloutLine.setAlpha(0f);
                        holder.calloutLine.setTranslationX(translation);*/
                    } else {
                        holder.iconView.setAlpha(0f);
                        holder.iconView.setTranslationY(translation);
                    }
                    if (!mWaitingForWindowAnimation) {
                        animateInIconOfFirstTask();
                    }
                }
            }

            holder.thumbnailView.setTag(td);
            holder.thumbnailView.setOnLongClickListener(new OnLongClickDelegate(convertView));
            holder.taskDescription = td;
            return convertView;
        }

        public void recycleView(View v) {
            ViewHolder holder = (ViewHolder) v.getTag();
            updateThumbnail(holder, mRecentTasksLoader.getDefaultThumbnail(), false, false);
            holder.iconView.setImageDrawable(mRecentTasksLoader.getDefaultIcon());
            holder.iconView.setVisibility(INVISIBLE);
            holder.iconView.animate().cancel();
            holder.labelView.setText(null);
            holder.labelView.animate().cancel();
            holder.thumbnailView.setContentDescription(null);
            holder.thumbnailView.setTag(null);
            holder.thumbnailView.setOnLongClickListener(null);
            holder.thumbnailView.setVisibility(INVISIBLE);
            holder.iconView.setAlpha(1f);
            holder.iconView.setTranslationX(0f);
            holder.iconView.setTranslationY(0f);
            holder.labelView.setAlpha(1f);
            holder.labelView.setTranslationX(0f);
            holder.labelView.setTranslationY(0f);

            holder.taskDescription = null;
            holder.loadedThumbnailAndIcon = false;
        }
    }

    public RecentsPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateValuesFromResources();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecentsPanelView,
                defStyle, 0);

        mRecentItemLayoutId = a.getResourceId(R.styleable.RecentsPanelView_recentItemLayout, 0);
        mRecentTasksLoader = RecentTasksLoader.getInstance(context);
        a.recycle();
    }

    public int numItemsInOneScreenful() {
        return mRecentsContainer.numItemsInOneScreenful();
    }

    private boolean pointInside(int x, int y, View v) {
        final int l = v.getLeft();
        final int r = v.getRight();
        final int t = v.getTop();
        final int b = v.getBottom();
        return x >= l && x < r && y >= t && y < b;
    }

    public boolean isInContentArea(int x, int y) {
        return pointInside(x, y, (View) mRecentsContainer);
    }

    public void dismissContextMenuIfAny() {
        if(mPopup != null) {
            mPopup.dismiss();
        }
    }
    public void show(boolean show) {
        show(show, null, false, false);
    }

    public void show(boolean show, ArrayList<TaskDescription> recentTaskDescriptions,
            boolean firstScreenful, boolean animateIconOfFirstTask) {
        if (show && mCallUiHiddenBeforeNextReload) {
            onUiHidden();
            recentTaskDescriptions = null;
            mAnimateIconOfFirstTask = false;
            mWaitingForWindowAnimation = false;
        } else {
            mAnimateIconOfFirstTask = animateIconOfFirstTask;
            mWaitingForWindowAnimation = animateIconOfFirstTask;
        }
        if (show) {
            mWaitingToShow = true;
            refreshRecentTasksList(recentTaskDescriptions, firstScreenful);
            refreshAllAppsList();
            showIfReady();
        } else {
            showImpl(false);
        }
    }

    private void showIfReady() {
        // mWaitingToShow => there was a touch up on the recents button
        // mRecentTaskDescriptions != null => we've created views for the first screenful of items
        if (mWaitingToShow && mRecentTaskDescriptions != null) {
            showImpl(true);
        }
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    private void showImpl(boolean show) {
        sendCloseSystemWindows(mContext, BaseStatusBar.SYSTEM_DIALOG_REASON_RECENT_APPS);

        mShowing = show;

        if (show) {
            // if there are no apps, bring up a "No recent apps" message
            boolean noApps = mRecentTaskDescriptions != null
                    && (mRecentTaskDescriptions.size() == 0);
            mRecentsNoApps.setAlpha(1f);
            mRecentsNoApps.setVisibility(noApps ? View.VISIBLE : View.INVISIBLE);

//MultiWindow NJ lenovo liming11 modify 2014-01-13 begin
            mRecentsPanelIndicator.setVisibility(View.VISIBLE);
            //showPanelIndicator();
            for(int i = 0; i<subPanelView.length; i++)
                subPanelView[i].setOnTouchListener(new TouchPanelViewSwitchListener(i));
            //mResizerView.setVisibility(View.INVISIBLE);
            try {
                List<StackBoxInfo> sbis = ActivityManagerNative.getDefault()
                        .getStackBoxes();
                if (sbis.size() > 1) {
                    for (StackBoxInfo stackBoxInfo : sbis) {
                        if ((stackBoxInfo.stackId != 0)
                                && (stackBoxInfo.children != null)) {
                            //mResizerView.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
//MultiWindow NJ lenovo liming11 add 2013-12-06 end

            onAnimationEnd(null);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        } else {
            mWaitingToShow = false;
            // call onAnimationEnd() and clearRecentTasksList() in onUiHidden()
            mCallUiHiddenBeforeNextReload = true;
            if (mPopup != null) {
                mPopup.dismiss();
            }
            gonePanelPreview();
        ((RecentsActivity) mContext).moveTaskToBack(true);
        }
    }

    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        final ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
    }

    public void onUiHidden() {
        mCallUiHiddenBeforeNextReload = false;
        if (!mShowing && mRecentTaskDescriptions != null) {
            onAnimationEnd(null);
            clearRecentTasksList();
        }
    }

    public void dismiss() {
        ((RecentsActivity) mContext).dismissAndGoHome();
    }

    public void dismissAndGoBack() {
        ((RecentsActivity) mContext).dismissAndGoBack();
    }

    public void onAnimationCancel(Animator animation) {
    }

    public void onAnimationEnd(Animator animation) {
        if (mShowing) {
            final LayoutTransition transitioner = new LayoutTransition();
            ((ViewGroup)mRecentsContainer).setLayoutTransition(transitioner);
            createCustomAnimations(transitioner);
        } else {
            ((ViewGroup)mRecentsContainer).setLayoutTransition(null);
        }
    }

    public void onAnimationRepeat(Animator animation) {
    }

    public void onAnimationStart(Animator animation) {
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Ignore hover events outside of this panel bounds since such events
        // generate spurious accessibility events with the panel content when
        // tapping outside of it, thus confusing the user.
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return super.dispatchHoverEvent(event);
        }
        return true;
    }

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
    public boolean isShowing() {
        return mShowing;
    }

    public void setRecentTasksLoader(RecentTasksLoader loader) {
        mRecentTasksLoader = loader;
    }

    public void updateValuesFromResources() {
        final Resources res = mContext.getResources();
        mThumbnailWidth = Math.round(res.getDimension(R.dimen.status_bar_recents_thumbnail_width));
        mFitThumbnailToXY = res.getBoolean(R.bool.config_recents_thumbnail_image_fits_to_xy);
        defaultBackground = getResources().getDrawable(R.drawable.panel_background_drawable);
        animationBackground = getResources().getDrawable(R.drawable.panel_animation_background_drawable);
        hoverBackground = getResources().getDrawable(R.drawable.panel_hover_background_drawable);
        filledBackground = getResources().getDrawable(R.drawable.panel_filled_background_drawable);
        mAppPaddingSpacing = (int)getContext().getResources().getDimension(R.dimen.recents_all_apps_paddingHorSpacing);
        //defaultEdgingBackground = getResources().getDrawable(R.drawable.panel_edging_drawable);
        //highlightEdgingBackground = getResources().getDrawable(R.drawable.panel_highlight_edging_drawable);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mRecentsContainer = (RecentsScrollView) findViewById(R.id.recents_container);
        mRecentsContainer.setOnScrollListener(new Runnable() {
            public void run() {
                // need to redraw the faded edges
                invalidate();
            }
        });
        mListAdapter = new TaskDescriptionAdapter(mContext);
        mRecentsContainer.setAdapter(mListAdapter);
        mRecentsContainer.setCallback(this);

        mHandler = new MyHandler();
        mRecentsScrim = findViewById(R.id.recents_bg_protect);
        mRecentsNoApps = findViewById(R.id.recents_no_apps);

        mAppsLauncherView0 = (GridView)findViewById(R.id.apps_launcher_sub0);
        mAppsLauncherView1 = (GridView)findViewById(R.id.apps_launcher_sub1);

        if (!mWaitingForWindowAnimation) {
            animateInIconOfFirstTask();
        }
        if (mRecentsScrim != null) {
            mHighEndGfx = ActivityManager.isHighEndGfx();
            if (!mHighEndGfx) {
                mRecentsScrim.setBackground(null);
            } else if (mRecentsScrim.getBackground() instanceof BitmapDrawable) {
                // In order to save space, we make the background texture repeat in the Y direction
                ((BitmapDrawable) mRecentsScrim.getBackground()).setTileModeY(TileMode.REPEAT);
            }
        }
    }

    private void refreshAllAppsList(){
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        int mScreenWidth = dm.widthPixels;
        int mScreenHeight = dm.heightPixels;
        float m = getContext().getResources().getDisplayMetrics().density;
        final int mScreenDip = (int)(mScreenWidth / m + 0.5f);
        //Log.v("DisplayMetrics","screenWidth == " + screenWidth + "screenDip == " + screenDip + " screenHeight == " + screenHeight);
        launcherItemSize = mAppsLauncherView0.getRequestedColumnWidth();
        final int mHorizontalSpacing = mAppsLauncherView0.getRequestedHorizontalSpacing();
        final int mlauncherAppWidth = (int)(launcherItemSize / m + 0.5f) ;
        final int mlauncherAppHorSpacing = (int)(mHorizontalSpacing / m + 0.5f) ;

        /*OnItemClickListener launcherIconClick = new OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ResolveInfo info = (ResolveInfo)parent.getAdapter().getItem(position);
                launchResolverByInfo(info,false);
            }
        };*/
    
        final OnItemLongClickListener launcherIconLongClick = new OnItemLongClickListener(){
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showPanelIndicator();
                showPanelDraging();
                ResolveInfo info = (ResolveInfo)parent.getAdapter().getItem(position);
                prepareDragDrop(-1,launcherIconDrageListener);
                ClipData data = ClipData.newPlainText("dot", "Dot : " + view.toString());
                view.startDrag(data,new DragShadowBuilder(view), (Object)info, 0);
                return true;
            }
        };

        /*Begin,Lenovo-sw Tom_liming11 modify 2014-02-27 for Asynchronous loading all app icons*/
        (new AsyncTask<Void, Void, Integer>() {
            @Override protected Integer doInBackground(Void... params) {
                //SystemClock.sleep(250);
                int mLine = loadApps(mScreenDip,mlauncherAppWidth,mlauncherAppHorSpacing);
                return mLine;
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                if(2 == result){
                    mAppsLauncherView0.setAdapter(new AppsLauncherAdapterSub0());
                    mAppsLauncherView1.setAdapter(new AppsLauncherAdapterSub1());
                }else {
                    mAppsLauncherView0.setAdapter(new AppsLauncherAdapterSub0());
                }
                final Configuration config = getResources().getConfiguration();
                int size = mAppsLauncherView0.getAdapter().getCount();
                int allWidth = (int) (launcherItemSize*size + mHorizontalSpacing*(size-1)+ 10);//10 makes the list more fancy
                int itemWidth = (int) (launcherItemSize);

                final LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                        allWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
                mAppsLauncherView0.setLayoutParams(param);
                mAppsLauncherView0.setNumColumns(size);
                mAppsLauncherView0.setOnTouchListener(new TouchAllappsIconListener());
                mAppsLauncherView0.setOnItemLongClickListener(launcherIconLongClick);
                if(2 == result){
                    int size1 = mAppsLauncherView1.getAdapter().getCount();
                    mAppsLauncherView1.setLayoutParams(param);
                    mAppsLauncherView1.setNumColumns(size1);
                    mAppsLauncherView1.setOnTouchListener(new TouchAllappsIconListener());
                    mAppsLauncherView1.setOnItemLongClickListener(launcherIconLongClick);
                }
            }
        }).execute();
        /*End,Lenovo-sw Tom_liming11 modify 2014-02-27 for Asynchronous loading all app icons*/
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
                switch (msg.what) {
                case SHOW_RED_WARNING:
                    subPromptView.setText(R.string.warning_prompt_info);
                    subPromptView.setTextColor(Color.RED);
                    subForbidView.setVisibility(View.VISIBLE);
                    subForbidView.setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_forbid_bg));
                break;
                case SHOW_WHITE_WARNING:
                    subPromptView.setTextColor(Color.WHITE);
                    mHandler.sendEmptyMessageDelayed(SHOW_RED_WARNING, 200);
                break;
                case SHOW_WHITE_NORMAL:
                    subPromptView.setText(R.string.prompt_info);
                    subPromptView.setTextColor(Color.WHITE);
                    subForbidView.setVisibility(View.GONE);
                    break;
                }
            }
        }

    @Override
    public boolean onTouch(View arg0, MotionEvent arg1) {
            float xPosition = arg1.getX();
            float yPosition = arg1.getY();
            int positon = ((GridView)arg0).pointToPosition((int)xPosition, (int)yPosition);
            if (positon == -1) {
                return false;
            }
            ResolveInfo info = (ResolveInfo)((GridView)arg0).getAdapter().getItem(positon);
            View child = getChildAtPosition(arg1,((GridView)arg0));
            if (templeView != null && templeView != child) {
                templeView.setBackgroundColor(Color.TRANSPARENT);
            }
            final int action = arg1.getAction();
            switch (action) {
            case MotionEvent.ACTION_DOWN:

                templeView = child;
                mInitialTouchPosY = arg1.getY();
                mInitialTouchPosX = arg1.getX();

            case MotionEvent.ACTION_MOVE:
            //float space = moveSpace(arg1,mInitialTouchPosX,mInitialTouchPos);

            if (arg1.getY() + 5 < mInitialTouchPosY) {
                inMultiWindowMode = true;
                if(DEBUG) Log.v("Tom","view == " + arg0);
                showPanelIndicator();
                showPanelDraging();
                prepareDragDrop(-1,launcherIconDrageListener);
                ClipData data = ClipData.newPlainText("dot", "Dot : " + child.toString());
                arg0.startDrag(data,new DragShadowBuilder(child), (Object)info, 0);
            } else {
                inMultiWindowMode = false;
            }
            break;
            
            case MotionEvent.ACTION_CANCEL:
                if(DEBUG) Log.v("Tom","ACTION_CANCEL ");
                break;
            
            case MotionEvent.ACTION_UP:
                if(DEBUG) Log.v("Tom","ACTION_UP");
            child.setBackgroundColor(Color.YELLOW);
            if (!inMultiWindowMode) {
                launchResolverByInfo(info,false);
            } else {
                inMultiWindowMode = false;
            }
            break;
    }
      return true;
    }

    public class TouchAllappsIconListener implements View.OnTouchListener {
        public boolean onTouch(View arg0, MotionEvent arg1) {
            float xPosition = arg1.getX();
            float yPosition = arg1.getY();
            int positon = ((GridView)arg0).pointToPosition((int)xPosition, (int)yPosition);
            if (positon == -1) {
                return false;
            }
            ResolveInfo info = (ResolveInfo)((GridView)arg0).getAdapter().getItem(positon);
            View child = getChildAtPosition(arg1,((GridView)arg0));
            if (templeView != null && templeView != child) {
                templeView.setBackgroundColor(Color.TRANSPARENT);
            }

            final int action = arg1.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    templeView = child;
                    mInitialTouchPosY = arg1.getY();
                    mInitialTouchPosX = arg1.getX();

                case MotionEvent.ACTION_MOVE:
                //float space = moveSpace(arg1,mInitialTouchPosX,mInitialTouchPos);

                if (arg1.getY() + 5 < mInitialTouchPosY) {
                    inMultiWindowMode = true;
                    if(DEBUG) Log.v("Tom","view == " + arg0);
                    showPanelIndicator();
                    showPanelDraging();
                    prepareDragDrop(-1,launcherIconDrageListener);
                    ClipData data = ClipData.newPlainText("dot", "Dot : " + child.toString());
                    arg0.startDrag(data,new DragShadowBuilder(child), (Object)info, 0);
                } else {
                    inMultiWindowMode = false;
                }
                break;

                case MotionEvent.ACTION_CANCEL:
                    if(DEBUG) Log.v("Tom","ACTION_CANCEL ");
                    break;

                case MotionEvent.ACTION_UP:
                    if(DEBUG) Log.v("Tom","ACTION_UP");
                child.setBackgroundColor(Color.YELLOW);
                if (!inMultiWindowMode) {
                    launchResolverByInfo(info,false);
                } else {
                    inMultiWindowMode = false;
                }
                break;
            }
            return false;
        }
    }
    public View getChildAtPosition(MotionEvent ev,GridView view) {
        final float x = ev.getX() + getScrollX();
        final float y = ev.getY() + getScrollY();
        for (int i = 0; i < view.getChildCount(); i++) {
            View item = view.getChildAt(i);
            if (x >= item.getLeft() && x < item.getRight()
                && y >= item.getTop() && y < item.getBottom()) {
                return item;
            }
        }
        return null;
    }

    public void removeBackground() {
        if (templeView != null) {
            templeView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void setMinSwipeAlpha(float minAlpha) {
        mRecentsContainer.setMinSwipeAlpha(minAlpha);
    }

    private void createCustomAnimations(LayoutTransition transitioner) {
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
    }

    private void updateIcon(ViewHolder h, Drawable icon, boolean show, boolean anim) {
        if (icon != null) {
            h.iconView.setImageDrawable(icon);
            if (show && h.iconView.getVisibility() != View.VISIBLE) {
                if (anim) {
                    h.iconView.setAnimation(
                            AnimationUtils.loadAnimation(mContext, R.anim.recent_appear));
                }
                h.iconView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateThumbnail(ViewHolder h, Drawable thumbnail, boolean show, boolean anim) {
        if (thumbnail != null) {
            // Should remove the default image in the frame
            // that this now covers, to improve scrolling speed.
            // That can't be done until the anim is complete though.
            h.thumbnailViewImage.setImageDrawable(thumbnail);

            // scale the image to fill the full width of the ImageView. do this only if
            // we haven't set a bitmap before, or if the bitmap size has changed
            if (h.thumbnailViewDrawable == null ||
                h.thumbnailViewDrawable.getIntrinsicWidth() != thumbnail.getIntrinsicWidth() ||
                h.thumbnailViewDrawable.getIntrinsicHeight() != thumbnail.getIntrinsicHeight()) {
                if (mFitThumbnailToXY) {
                    h.thumbnailViewImage.setScaleType(ScaleType.FIT_XY);
                } else {
                    Matrix scaleMatrix = new Matrix();
                    float scale = mThumbnailWidth / (float) thumbnail.getIntrinsicWidth();
                    scaleMatrix.setScale(scale, scale);
                    h.thumbnailViewImage.setScaleType(ScaleType.MATRIX);
                    h.thumbnailViewImage.setImageMatrix(scaleMatrix);
                }
            }
            if (show && h.thumbnailView.getVisibility() != View.VISIBLE) {
                if (anim) {
                    h.thumbnailView.setAnimation(
                            AnimationUtils.loadAnimation(mContext, R.anim.recent_appear));
                }
                h.thumbnailView.setVisibility(View.VISIBLE);
            }
            h.thumbnailViewDrawable = thumbnail;
        }
    }

    void onTaskThumbnailLoaded(TaskDescription td) {
        synchronized (td) {
            if (mRecentsContainer != null) {
                ViewGroup container = (ViewGroup) mRecentsContainer;
                if (container instanceof RecentsScrollView) {
                    container = (ViewGroup) container.findViewById(
                            R.id.recents_linear_layout);
                }
                // Look for a view showing this thumbnail, to update.
                for (int i=0; i < container.getChildCount(); i++) {
                    View v = container.getChildAt(i);
                    if (v.getTag() instanceof ViewHolder) {
                        ViewHolder h = (ViewHolder)v.getTag();
                        if (!h.loadedThumbnailAndIcon && h.taskDescription == td) {
                            // only fade in the thumbnail if recents is already visible-- we
                            // show it immediately otherwise
                            //boolean animateShow = mShowing &&
                            //    mRecentsContainer.getAlpha() > ViewConfiguration.ALPHA_THRESHOLD;
                            boolean animateShow = false;
                            updateIcon(h, td.getIcon(), true, animateShow);
                            updateThumbnail(h, td.getThumbnail(), true, animateShow);
                            h.loadedThumbnailAndIcon = true;
                        }
                    }
                }
            }
        }
        showIfReady();
    }

    private void animateInIconOfFirstTask() {
        if (mItemToAnimateInWhenWindowAnimationIsFinished != null &&
                !mRecentTasksLoader.isFirstScreenful()) {
            int timeSinceWindowAnimation =
                    (int) (System.currentTimeMillis() - mWindowAnimationStartTime);
            final int minStartDelay = 150;
            final int startDelay = Math.max(0, Math.min(
                    minStartDelay - timeSinceWindowAnimation, minStartDelay));
            final int duration = 250;
            final ViewHolder holder = mItemToAnimateInWhenWindowAnimationIsFinished;
            final TimeInterpolator cubic = new DecelerateInterpolator(1.5f);
            FirstFrameAnimatorHelper.initializeDrawListener(holder.iconView);
            for (View v :
                new View[] { holder.iconView, holder.labelView}) {
                if (v != null) {
                    ViewPropertyAnimator vpa = v.animate().translationX(0).translationY(0)
                            .alpha(1f).setStartDelay(startDelay)
                            .setDuration(duration).setInterpolator(cubic);
                    FirstFrameAnimatorHelper h = new FirstFrameAnimatorHelper(vpa, v);
                }
            }
            mItemToAnimateInWhenWindowAnimationIsFinished = null;
            mAnimateIconOfFirstTask = false;
        }
    }

    public void onWindowAnimationStart() {
        mWaitingForWindowAnimation = false;
        mWindowAnimationStartTime = System.currentTimeMillis();
        animateInIconOfFirstTask();
    }

    public void clearRecentTasksList() {
        // Clear memory used by screenshots
        if (mRecentTaskDescriptions != null) {
            mRecentTasksLoader.cancelLoadingThumbnailsAndIcons(this);
            onTaskLoadingCancelled();
        }
    }

    public void onTaskLoadingCancelled() {
        // Gets called by RecentTasksLoader when it's cancelled
        if (mRecentTaskDescriptions != null) {
            mRecentTaskDescriptions = null;
            mListAdapter.notifyDataSetInvalidated();
        }
    }

    public void refreshViews() {
        mListAdapter.notifyDataSetInvalidated();
        updateUiElements();
        showIfReady();
    }

    public void refreshRecentTasksList() {
        refreshRecentTasksList(null, false);
    }

    private void refreshRecentTasksList(
            ArrayList<TaskDescription> recentTasksList, boolean firstScreenful) {
        if (mRecentTaskDescriptions == null && recentTasksList != null) {
            onTasksLoaded(recentTasksList, firstScreenful);
        } else {
            mRecentTasksLoader.loadTasksInBackground();
        }
    }

    public void onTasksLoaded(ArrayList<TaskDescription> tasks, boolean firstScreenful) {
        if (mRecentTaskDescriptions == null) {
            mRecentTaskDescriptions = new ArrayList<TaskDescription>(tasks);
        } else {
            mRecentTaskDescriptions.addAll(tasks);
        }
        if (((RecentsActivity) mContext).isActivityShowing()) {
            refreshViews();
        }
    }

    private void updateUiElements() {
        final int items = mRecentTaskDescriptions != null
                ? mRecentTaskDescriptions.size() : 0;

        ((View) mRecentsContainer).setVisibility(items > 0 ? View.VISIBLE : View.GONE);

        // Set description for accessibility
        int numRecentApps = mRecentTaskDescriptions != null
                ? mRecentTaskDescriptions.size() : 0;
        String recentAppsAccessibilityDescription;
        if (numRecentApps == 0) {
            recentAppsAccessibilityDescription =
                getResources().getString(R.string.status_bar_no_recent_apps);
        } else {
            recentAppsAccessibilityDescription = getResources().getQuantityString(
                R.plurals.status_bar_accessibility_recent_apps, numRecentApps, numRecentApps);
        }
        setContentDescription(recentAppsAccessibilityDescription);
    }

    public boolean simulateClick(int persistentTaskId) {
        View v = mRecentsContainer.findViewForTask(persistentTaskId);
        if (v != null) {
            handleOnClick(v);
            return true;
        }
        return false;
    }

    public void handleOnClick(View view) {
        handleOnClick(view, false);
    }

    private void handleOnClick(View view, boolean isSimulateClick) {
        ViewHolder holder = (ViewHolder)view.getTag();
        TaskDescription ad = holder.taskDescription;
        final Context context = view.getContext();
        final ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);

        Bitmap bm = null;
        boolean usingDrawingCache = true;
        if (holder.thumbnailViewDrawable instanceof BitmapDrawable) {
            bm = ((BitmapDrawable) holder.thumbnailViewDrawable).getBitmap();
            if (bm.getWidth() == holder.thumbnailViewImage.getWidth() &&
                    bm.getHeight() == holder.thumbnailViewImage.getHeight()) {
                usingDrawingCache = false;
            }
        }
        if (usingDrawingCache) {
            holder.thumbnailViewImage.setDrawingCacheEnabled(true);
            bm = holder.thumbnailViewImage.getDrawingCache();
        }
        Bundle opts = (bm == null) ?
                null :
                ActivityOptions.makeThumbnailScaleUpAnimation(
                        holder.thumbnailViewImage, bm, 0, 0, null).toBundle();

        show(false);
        if (ad.taskId >= 0) {
            // This is an active task; it should just go to the foreground.
            am.moveTaskToFront(ad.taskId, ActivityManager.MOVE_TASK_WITH_HOME,
                    opts);
            try {
                if (!isSimulateClick) {
                    ActivityManagerNative.getDefault().setFocusedTask(ad.taskId);
                } else {
                    int stackId = ActivityManagerNative.getDefault().getFocusedStack2();
                    ActivityManagerNative.getDefault().setFocusedStack(stackId);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "", e);
            }
        } else {
            Intent intent = ad.intent;
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DEBUG) Log.v(TAG, "Starting activity " + intent);
            try {
                context.startActivityAsUser(intent, opts,
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (SecurityException e) {
                Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error launching activity " + intent, e);
            }
        }
        if (usingDrawingCache) {
            holder.thumbnailViewImage.setDrawingCacheEnabled(false);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        handleOnClick(view);
    }

    public void handleSwipe(View view) {
        TaskDescription ad = ((ViewHolder) view.getTag()).taskDescription;
        if (ad == null) {
            Log.v(TAG, "Not able to find activity description for swiped task; view=" + view +
                    " tag=" + view.getTag());
            return;
        }
        if (DEBUG) Log.v(TAG, "Jettison " + ad.getLabel());
        mRecentTaskDescriptions.remove(ad);
        mRecentTasksLoader.remove(ad);

        // Handled by widget containers to enable LayoutTransitions properly
        // mListAdapter.notifyDataSetChanged();

        if (mRecentTaskDescriptions.size() == 0) {
            dismissAndGoBack();
        }

        // Currently, either direction means the same thing, so ignore direction and remove
        // the task.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.removeTask(ad.persistentTaskId, ActivityManager.REMOVE_TASK_KILL_PROCESS);

            // Accessibility feedback
            setContentDescription(
                    mContext.getString(R.string.accessibility_recents_item_dismissed, ad.getLabel()));
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            setContentDescription(null);
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(getContext())
                .addNextIntentWithParentStack(intent).startActivities();
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mPopup != null) {
            return true;
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

//MultiWindow NJ lenovo liming11 add 2013-12-06 begin

    /*Begin,Lenovo-sw liming11 add 2014-01-08, modify for MultiWindow */
    TextView subPromptView = null;
    View viewSpacing[] = {null,null,null};
    ImageView subPanelView[] = {null,null,
            null,null};
    ImageView subForbidView = null;
    View subLayoutPanelView[] = {null,null};
    //Drawable defaultEdgingBackground = null;
    //Drawable highlightEdgingBackground = null;
    /*End,Lenovo-sw liming11 add 2014-01-08, modify for MultiWindow */
    GridView appView = null;
    Drawable animationBackground = null;
    Drawable defaultBackground = null;
    Drawable hoverBackground = null;
    Drawable filledBackground = null;
    int DURATION = 500;
    int PANEL_EMPTY=0;
    int PANEL_OCCUPIED=1;
    private Handler mHandler;
    private static final int SHOW_RED_WARNING = 1;
    private static final int SHOW_WHITE_WARNING = 2;
    private static final int SHOW_WHITE_NORMAL = 3;

    public void RotationInAnimation(final View targetView,boolean useAnimationBackground,boolean recoverBackgroundAfterAnmiation){
        targetView.setPivotX(targetView.getLeft()+targetView.getWidth()/2);
        targetView.setPivotY(targetView.getBottom());
        PropertyValuesHolder pvhR = PropertyValuesHolder.ofFloat("Rotation",-90, 0f);
        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha",0, 1);
        ObjectAnimator whxyBouncer = ObjectAnimator.ofPropertyValuesHolder(targetView,pvhR, pvhAlpha).setDuration(DURATION);
        if (useAnimationBackground){
            targetView.setBackgroundColor(getResources().getColor(R.color.animationBackgroundColor));
            if (recoverBackgroundAfterAnmiation){ 
                whxyBouncer.addListener(resetBackground(targetView));
            }            
        }
        whxyBouncer.start();
    }
    
    public void DropInAnimation(final View targetView,boolean useAnimationBackground,boolean recoverBackgroundAfterAnmiation){
        PropertyValuesHolder pvhSX = PropertyValuesHolder.ofFloat("ScaleX", 0f,1f);
        PropertyValuesHolder pvhSY = PropertyValuesHolder.ofFloat("ScaleY", 0f,1f);
        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha",0, 1);
        ObjectAnimator whxyBouncer = ObjectAnimator.ofPropertyValuesHolder(targetView,pvhSX,pvhSY, pvhAlpha).setDuration(DURATION);
        if (useAnimationBackground){
            //targetView.setBackground(animationBackground);
            if (recoverBackgroundAfterAnmiation){ 
                whxyBouncer.addListener(resetBackground(targetView));
            }            
        }
        whxyBouncer.start();
    }


    public void FlyOutAnimation(final View targetView,boolean positiveDirection, boolean useAnimationBackground,boolean recoverBackgroundAfterAnmiation){
        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha", 1, 0,0);
        PropertyValuesHolder pvTY;
        if (positiveDirection){
            pvTY = PropertyValuesHolder.ofFloat("y", targetView.getY(),targetView.getY()-targetView.getHeight(), targetView.getY());
        }else{
            pvTY = PropertyValuesHolder.ofFloat("y", targetView.getY(),targetView.getY()+ targetView.getHeight(), targetView.getY());
        }
        ObjectAnimator whxyBouncer = ObjectAnimator.ofPropertyValuesHolder(targetView, pvTY,pvhAlpha).setDuration(DURATION);
        //whxyBouncer.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        if (useAnimationBackground){
            targetView.setBackground(animationBackground);
            if (recoverBackgroundAfterAnmiation){ 
                whxyBouncer.addListener(resetBackground(targetView));
            }
        }        
        whxyBouncer.start();
    }
    
    public void FlyInAnimation(final View targetView,boolean positiveDirection, boolean useAnimationBackground,boolean recoverBackgroundAfterAnmiation){

        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha", 0,1);
        PropertyValuesHolder pvTY;
        if (positiveDirection){
            pvTY = PropertyValuesHolder.ofFloat("y", targetView.getY()-targetView.getHeight(), targetView.getY());
        }else{
            pvTY = PropertyValuesHolder.ofFloat("y", targetView.getY()+ targetView.getHeight(), targetView.getY());
        }
        ObjectAnimator whxyBouncer = ObjectAnimator.ofPropertyValuesHolder(targetView, pvTY,pvhAlpha).setDuration(DURATION);
        //whxyBouncer.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        if (useAnimationBackground){
            targetView.setBackground(animationBackground);
            if (recoverBackgroundAfterAnmiation){ 
                whxyBouncer.addListener(resetBackground(targetView));
            }            
        }           
        whxyBouncer.start();
    }

    private AnimatorListener resetBackground(final View targetView) {
        AnimatorListener ret = new AnimatorListener() {
            
            @Override
            public void onAnimationCancel(Animator animation) {
                targetView.setBackgroundColor(getResources().getColor(R.color.defaultBackgroundColor));
                targetView.setAlpha(1);
                targetView.invalidate();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                targetView.setBackgroundColor(getResources().getColor(R.color.defaultBackgroundColor));
                targetView.setAlpha(1);
                targetView.invalidate();
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            // TODO Auto-generated method stub
            }

            @Override
            public void onAnimationStart(Animator animation) {
                // TODO Auto-generated method stub
            }
        };
        
        return ret;
    }

    /*Lenovo sw start, xieqiong2 2014.1.22 BLADEFHD-283*/
    private int getTaskIdByDragEvent(DragEvent event) {
        ResolveInfo info = (ResolveInfo)event.getLocalState();
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        ComponentName cn = new ComponentName(
                           info.activityInfo.applicationInfo.packageName,
                           info.activityInfo.name);
        int taskId = sbiu.getTaskIdFromNameInfo(cn.flattenToString());
        return taskId;
    }

    private int findStackIdByDragApp(DragEvent event) {
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        int taskId = getTaskIdByDragEvent(event);
        int stackId = sbiu.getStackIdByTaskId(taskId);
        return stackId;
    }
    /*Lenovo sw end, xieqiong2 2014.1.22 BLADEFHD-283*/

    void upDateViewByTag(View view,boolean enter, boolean disable){
        //it is need 
        int i = 0;
        for(int j = 0; j < subPanelView.length; j++){
            if(view == subPanelView[j]){
                i = j;
                break;
            }
            
        }
        
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        Configuration config = getResources().getConfiguration();
        boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
        
        if(portMode){
            
            if (enter){
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit){
                    
                    if(3 == i && null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));
                            }
                        }
                    }else if(2 == i){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_blue_bg));
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));
                            }
                        }
                    }
                }else{
                    if(i>=sbiu.getTotalWindows()){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_blue_bg));
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));    
                            }
                        }
                    }
                }
            }else {
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit){
                    if(2 == i){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    }else if(3 == i && null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));    
                            }
                        }
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));
                            }
                        }
                    }
                }else{
                    if(i>=sbiu.getTotalWindows()){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));
                            }
                        }
                    }
                }
            }
        }else{
            if (enter){
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit){
                    if(2 == i){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_blue_bg));
                    }else if(3 == i && null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));    
                            }
                        }
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));
                            }
                        }
                    }
                }else{
                    if(i>=sbiu.getTotalWindows()){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_blue_bg));
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_blue_bg)));    
                            }
                        }
                    }
                }
            }else {
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit){
                    if(2 == i){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    }else if(3 == i && null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));    
                            }
                        }
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));
                            }
                        }
                    }
                }else{
                    if(i>=sbiu.getTotalWindows()){
                        subPanelView[i].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    }else if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() > 0){    
                        for(int j = 0; j<mRecentTaskDescriptions.size(); j++){
                            if(mRecentTaskDescriptions.get(j).taskId == sbiu.getTopTaskIdByIndex(i+1, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(j).getThumbnail();
                                subPanelView[i].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));
                            }
                        }
                    }
                }
            }
        }
    }

    private void launchResolverByInfo(ResolveInfo info,boolean isDrag) {
//dingej1 2014,2,27 begin. BLADEFHD-2311 dismissAndGoBack will call show(false) before startActivity. Cause AMS wrong
        //RecentsPanelView.this.dismissAndGoBack(); //if we put this line to bottom of this function, new started app will be dismissed....
//dingej1 end.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(
                info.activityInfo.applicationInfo.packageName,
                info.activityInfo.name));
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;
        if (isDrag)
            flags |= Intent.FLAG_ACTIVITY_MULTIWINDOW;
        intent.setFlags(flags);

        if (DEBUG)
            Log.v(TAG, "Starting activity " + intent);
        try {
            RecentsPanelView.this.mContext.startActivityAsUser(intent, null,
                    new UserHandle(UserHandle.USER_CURRENT));

        } catch (SecurityException e) {
            Log.e(TAG, "Recents does not have the permission to launch "
                    + intent, e);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Error launching activity " + intent, e);
        }
//dingej1 2014,2,27 begin. BLADEFHD-2311 
        RecentsPanelView.this.show(false);
//dingej1 end.
    }

    /*Lenovo sw end, xieqiong2 add 2014.1.24 BLADEFHD-283*/
    boolean isLeftSplitTrend(int index) {
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        return ((index == 4)&&(sbiu.getTotalWindows() == 2));
    }
    /*Lenovo sw end, xieqiong2 add 2014.1.24 BLADEFHD-283*/


    OnDragListener launcherIconDrageListener = new OnDragListener(){
        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                // claim to accept any dragged content
                if(DEBUG) Log.i(TAG, "Drag started.");
                //changing
                
            } break;

            case DragEvent.ACTION_DRAG_ENDED: {
                if(DEBUG) Log.i(TAG, "Drag ended.");
                //when up,it's need show the start preview
                //showPanelIndicator();
                gonePanelPreview();
                //mRecentsPanelIndicator.setVisibility(View.INVISIBLE);
            } break;

            case DragEvent.ACTION_DRAG_LOCATION: {
                // we returned true to DRAG_STARTED, so return true here

            } break;

            case DragEvent.ACTION_DROP: {
                if(DEBUG) Log.i(TAG, "Got a drop! dot=" + this + " event=" + event);
                DropInAnimation(v,true,false);
                View targetView = v;
                    /*Lenovo sw end, xieqiong2 modify 2014.1.22 BLADEFHD-283*/
                    int taskId = getTaskIdByDragEvent(event);
                    int stackId = findStackIdByDragApp(event);
                StackBoxInfoUtils sbiu = new StackBoxInfoUtils();

                    int index;
                    //get the index from the targetview
                    for (index = 0; index < subPanelView.length; index++) {
                       if (targetView == subPanelView[index]) {
                            break;
                       }
                    }
                    index = index + 1; //window UI index starts from 1!

                    Configuration config = getResources().getConfiguration();
                    boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
                    WinDisplayMode mode = sbiu.getDisplayMode(portMode);

                    int targetStackId = sbiu.getStackIdByIndex(index, portMode);
                    if(stackId >= 0) {
                      try {
                        if(sbiu.getStackTaskSizeByStackId(stackId) == 1) {
                            ActivityManagerNative.getDefault().moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME, null);
                            ActivityManagerNative.getDefault().setFocusedTask(taskId);
                            //TODO, flash the window.
                        } else {
                            if(stackId == targetStackId) {
                                ActivityManagerNative.getDefault().moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME, null);
                                ActivityManagerNative.getDefault().setFocusedTask(taskId);
                            } else {
                                if(targetStackId < 0) {
                                   targetStackId = sbiu.addNewWindow(isLeftSplitTrend(index));
                                }
                                //sbiu.setTargetWindow(index, portMode);
                                dismissAndGoBack();
                                ActivityManagerNative.getDefault().moveTaskToStack(taskId, targetStackId, true);
                                ActivityManagerNative.getDefault().setFocusedTask(taskId);
                            }
                        }
                      } catch (RemoteException e) {
                        e.printStackTrace();
                      }
                    } else {
                        if(targetStackId >= 0) {
                            sbiu.setTargetWindow(index, portMode);
                        } else {
                            sbiu.addNewWindow(isLeftSplitTrend(index));
                        }
                        launchResolverByInfo((ResolveInfo)event.getLocalState(),true);
                    }
                    upDateViewByTag(v,false,false);
                    /*Lenovo sw end, xieqiong2 modify 2014.1.22 BLADEFHD-283*/
            } break;

            case DragEvent.ACTION_DRAG_ENTERED: {
                    /*Lenovo sw start, xieqiong2 modify 2014.1.22 BLADEFHD-283*/
                        int stackId = findStackIdByDragApp(event);
                        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
                        boolean disableView = ((stackId >= 0) && (sbiu.getStackTaskSizeByStackId(stackId) == 1))?true:false;
                        if(DEBUG) Log.v("ACTION_DRAG_ENTERED  ","v == " + v);
                        upDateViewByTag(v,true,false);
                        
                        //upDateViewByTag(v,true, disableView);
                    /*Lenovo sw end, xieqiong2 modify 2014.1.22 BLADEFHD-283*/
            } break;

            case DragEvent.ACTION_DRAG_EXITED: {
                upDateViewByTag(v,false,false);
            } break;

            default:
                break;
            }
            return true;
        }
    };


    OnDragListener recentAppDrageListener = new OnDragListener(){
        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                // claim to accept any dragged content
                if(DEBUG) Log.i(TAG, "Drag started.");
                if(!isInWhiteList){
                    mHandler.sendEmptyMessage(SHOW_RED_WARNING);
                    //subForbidView.setVisibility(View.VISIBLE);
                    //subForbidView.setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_forbid_bg));
                }

            } break;

            case DragEvent.ACTION_DRAG_ENDED: {
                if(DEBUG) Log.i(TAG, "Drag ended.");
                if(!isInWhiteList){
                    isInWhiteList = true;
                    mHandler.sendEmptyMessage(SHOW_WHITE_NORMAL);
                    //subForbidView.setVisibility(View.GONE);
                }
                gonePanelPreview();
                //showPanelIndicator();
                //mRecentsPanelIndicator.setVisibility(View.INVISIBLE);
            } break;

            case DragEvent.ACTION_DRAG_LOCATION: {
                // we returned true to DRAG_STARTED, so return true here

            } break;

            case DragEvent.ACTION_DROP: {
                //Log.i(TAG, "Got a drop! dot=" + this + " event=" + event);
                if(!isInWhiteList)
                    break;
                DropInAnimation(v,true,false);
                    /*Lenovo sw start, xieqiong2 add 2014.1.23 BLADEFHD-283*/
                View targetView = v;
                View orgView = (View)event.getLocalState();
                ViewHolder holder = (ViewHolder) orgView.getTag();
                TaskDescription ad = holder.taskDescription;
                StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
                int taskId = ad.taskId;
                int stackId = sbiu.getStackIdByTaskId(taskId);

                final ActivityManager am = (ActivityManager)RecentsPanelView.this.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                Bitmap bm = null;
                boolean usingDrawingCache = true;

                /*if (holder.thumbnailViewDrawable instanceof BitmapDrawable) {
                    bm = ((BitmapDrawable) holder.thumbnailViewDrawable).getBitmap();
                    if (bm.getWidth() == holder.thumbnailViewImage.getWidth() &&
                    bm.getHeight() == holder.thumbnailViewImage.getHeight()) {
                       usingDrawingCache = false;
                    }
                }*/
                if (usingDrawingCache) {
                   holder.thumbnailViewImage.setDrawingCacheEnabled(true);
                   bm = holder.thumbnailViewImage.getDrawingCache();
                }
                Bundle opts = (bm == null) ?
                   null :
                   ActivityOptions.makeThumbnailScaleUpAnimation(
                   holder.thumbnailViewImage, bm, 0, 0, null).toBundle();


                //calculate the index start,to get the index from the targetview
                int index;
                for (index = 0; index < subPanelView.length; index++) {
                   if (targetView == subPanelView[index]) {
                        break;
                   }
                }
                index = index + 1; //window UI index starts from 1!

                Configuration config = getResources().getConfiguration();
                boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
                WinDisplayMode mode = sbiu.getDisplayMode(portMode);

                int targetStackId = sbiu.getStackIdByIndex(index, portMode);
                if(stackId >= 0) {
                  try {
                    if(sbiu.getStackTaskSizeByStackId(stackId) == 1) {
                        ActivityManagerNative.getDefault().moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME, opts);
                        ActivityManagerNative.getDefault().setFocusedTask(taskId);
                        //TODO, flash the window.
                    } else {
                        if(stackId == targetStackId) {
                            ActivityManagerNative.getDefault().moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME, opts);
                            ActivityManagerNative.getDefault().setFocusedTask(taskId);
                        } else {
                            if(targetStackId < 0) {
                               targetStackId = sbiu.addNewWindow(isLeftSplitTrend(index));
                            }
                            //sbiu.setTargetWindow(index, portMode);
                            dismissAndGoBack();
                            ActivityManagerNative.getDefault().moveTaskToStack(taskId, targetStackId, true);
                            ActivityManagerNative.getDefault().setFocusedTask(taskId);
                        }
                    }
                  } catch (RemoteException e) {
                    e.printStackTrace();
                  }
                } else {
                   if(targetStackId >= 0) {
                        sbiu.setTargetWindow(index, portMode);
                   } else {
                        sbiu.addNewWindow(isLeftSplitTrend(index));
                   }
                   Intent intent = ad.intent;
                   intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                                   | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                                   | Intent.FLAG_ACTIVITY_MULTIWINDOW
                                   | Intent.FLAG_ACTIVITY_NEW_TASK);
                   if (DEBUG) Log.v(TAG, "Starting activity " + intent);
                   try {
                     RecentsPanelView.this.mContext.startActivityAsUser(intent, opts,
                         new UserHandle(UserHandle.USER_CURRENT));
                   } catch (SecurityException e) {
                      Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
                   } catch (ActivityNotFoundException e) {
                      Log.e(TAG, "Error launching activity " + intent, e);
                   }
                }
                upDateViewByTag(v,false,false);
                /*Lenovo sw end, xieqiong2 modify 2014.1.22 BLADEFHD-283*/

              if (usingDrawingCache) {
                   holder.thumbnailViewImage.setDrawingCacheEnabled(false);
              }
              //handleStartInStack(orgView,newStackId);
            }
            break;

            case DragEvent.ACTION_DRAG_ENTERED: {
                if(!isInWhiteList)
                    break;
                  /*Lenovo sw start, xieqiong2 modify 2014.1.23 BLADEFHD-283*/
                  View orgView = (View)event.getLocalState();
                  ViewHolder holder = (ViewHolder) orgView.getTag();
                  TaskDescription ad = holder.taskDescription;
                  StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
                  int stackId = sbiu.getStackIdByTaskId(ad.taskId);

                  boolean disableView = ((stackId >= 0) && (sbiu.getStackTaskSizeByStackId(stackId) == 1))?true:false;
                  upDateViewByTag(v,true,disableView);
                  /*Lenovo sw end, xieqiong2 modify 2014.1.23 BLADEFHD-283*/
            } break;

            case DragEvent.ACTION_DRAG_EXITED: {
                if(isInWhiteList)
                    upDateViewByTag(v,false, false);
            } break;

            default:
                break;
            }
            return true;
        }
    };
    
    private Bitmap createBitmap( Bitmap src, Bitmap watermark )
    {
        String tag = "createBitmap";
        Log.d( tag, "create a new bitmap" );
        if( src == null )
        {
            return null;
        }
 
        int w = src.getWidth();
        int h = src.getHeight();
        int ww = watermark.getWidth();
        int wh = watermark.getHeight();
        //create the new blank bitmap
        Bitmap newb = Bitmap.createBitmap( w, h, Bitmap.Config.ARGB_8888 );//创建一个新的和SRC长度宽度一样的位图
        Canvas cv = new Canvas( newb );
        //draw src into
        cv.drawBitmap( src, 0, 0, null );//在 0，0坐标开始画入src
        //draw watermark into
        cv.drawBitmap( watermark, w - ww + 5, h - wh + 5, null );//在src的右下角画入水印
        //save all clip
        cv.save( Canvas.ALL_SAVE_FLAG );//保存
        //store
        cv.restore();//存储
        return newb;
    }

    /*private Drawable bitmapToDrawable(Bitmap bitmap){
        Drawable drawable = new BitmapDrawable(mContext.getResources(),bitmap);
        return drawable;
    }*/
    private Drawable drawableStacked(Drawable aboveDrawable,Drawable belowDrable){
        //Resources r = getResources();
        Drawable[] layers = new Drawable[2];
        layers[0] = aboveDrawable;//r.getDrawable(R.drawable.recent_previewin_bg.9);
        layers[1] = belowDrable;//r.getDrawable(R.drawable.tt);
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        return layerDrawable;
    }
    
    
    public void showPanelDraging(){
        
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        if (DEBUG) Log.v(TAG,"sbiu.getTotalWindows() == " + sbiu.getTotalWindows());
        int m_winCount = sbiu.getTotalWindows();
        int m_previewCount = subPanelView.length;
        //initialization begin

        switch(m_winCount){
        case 1:
            viewSpacing[1].setVisibility(View.VISIBLE);
            subLayoutPanelView[1].setVisibility(View.VISIBLE);
            subPanelView[1].setVisibility(View.VISIBLE);
            subPanelView[1].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            break;
        case 2:
            //subLayoutPanelView[1].setVisibility(View.VISIBLE);
            viewSpacing[0].setVisibility(View.VISIBLE);
            viewSpacing[1].setVisibility(View.VISIBLE);
            viewSpacing[2].setVisibility(View.VISIBLE);
            
            subPanelView[2].setVisibility(View.VISIBLE);
            subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            subPanelView[3].setVisibility(View.VISIBLE);
            subPanelView[3].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            break;
        case 3:    
            
            Configuration config = getResources().getConfiguration();
            boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
            if(portMode){
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit){
                    viewSpacing[2].setVisibility(View.VISIBLE);
                    subPanelView[2].setVisibility(View.VISIBLE);
                    subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                }else{
                    viewSpacing[0].setVisibility(View.VISIBLE);
                    subPanelView[3].setVisibility(View.VISIBLE);
                    subPanelView[3].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                }
            }else{
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit){
                    viewSpacing[2].setVisibility(View.VISIBLE);
                    subPanelView[2].setVisibility(View.VISIBLE);
                    subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                }else{
                    viewSpacing[0].setVisibility(View.VISIBLE);
                    subPanelView[3].setVisibility(View.VISIBLE);
                    subPanelView[3].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                }
            }

            break;
        case 4:
            break;
        default:
            break;
        }
    }

    public void gonePanelPreview(){
        /*if(mRecentsPanelIndicator != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.lenovo_status_bar_recents_preview_exit);
            animation.reset();
            mRecentsPanelIndicator.setAnimation(animation);
        }*/
        for(int i = 0; i< subLayoutPanelView.length; i++){
            subLayoutPanelView[i].setVisibility(View.GONE);
        }
        for(int i = 0; i< viewSpacing.length; i++){
            viewSpacing[i].setVisibility(View.GONE);
        }
        for(int i = 0; i< subPanelView.length; i++){
            subPanelView[i].setVisibility(View.GONE);
        }
    }

    public void showPanelIndicator(){
        
        StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
        if (DEBUG) Log.v("showPanelIndicatior","sbiu.getTotalWindows() == " + sbiu.getTotalWindows());
        int m_winCount = sbiu.getTotalWindows();
        int m_previewCount = subPanelView.length;
        //initialization begin

        for(int i = 0; i< subLayoutPanelView.length; i++){
            subLayoutPanelView[i].setVisibility(View.GONE);
        }
        for(int i = 0; i< viewSpacing.length; i++){
            viewSpacing[i].setVisibility(View.GONE);
        }
        //initialization end
        
        for(int i = m_winCount; i< m_previewCount; i++){    
            subPanelView[i].setVisibility(View.GONE);
        }
        
        subLayoutPanelView[0].setVisibility(View.VISIBLE);
        subPanelView[0].setVisibility(View.VISIBLE);
        if(m_winCount == 0){
            subPanelView[0].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
        }else if(m_winCount == 2){
            subLayoutPanelView[1].setVisibility(View.VISIBLE);
            viewSpacing[1].setVisibility(View.VISIBLE);

            subPanelView[1].setVisibility(View.VISIBLE);
            //subPanelView[1].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
        }else if(m_winCount == 3){
            subLayoutPanelView[1].setVisibility(View.VISIBLE);
            viewSpacing[1].setVisibility(View.VISIBLE);
            subPanelView[1].setVisibility(View.VISIBLE);
            //subPanelView[1].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            Configuration config = getResources().getConfiguration();
            boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
            if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit || 
                    sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit){
                subPanelView[3].setVisibility(View.VISIBLE);
                //subPanelView[3].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            }else{
                subPanelView[2].setVisibility(View.VISIBLE);
                //subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            }
            
        }else if(m_winCount == 4){
            subLayoutPanelView[1].setVisibility(View.VISIBLE);
            viewSpacing[1].setVisibility(View.VISIBLE);
            viewSpacing[0].setVisibility(View.VISIBLE);
            viewSpacing[2].setVisibility(View.VISIBLE);
            subPanelView[1].setVisibility(View.VISIBLE);
            //subPanelView[1].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            subPanelView[2].setVisibility(View.VISIBLE);
            //subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
            subPanelView[3].setVisibility(View.VISIBLE);
            //subPanelView[3].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
        }
        
        if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() != 0){    
            int j = sbiu.getTotalWindows();
            Configuration config = getResources().getConfiguration();
            boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
            if(portMode){
                for(int k=1; k<=j; k++){
                    for(int i = 0; i<mRecentTaskDescriptions.size(); i++){
                        if(mRecentTaskDescriptions.get(i).taskId == sbiu.getTopTaskIdByIndex(k, true)){
                            Drawable drawable = mRecentTaskDescriptions.get(i).getThumbnail();
                            subPanelView[k-1].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));    
                        }
                    }
                }
            }else{
                for(int k=1; k<=j; k++){
                    for(int i = 0; i<mRecentTaskDescriptions.size(); i++){
                        if(mRecentTaskDescriptions.get(i).taskId == sbiu.getTopTaskIdByIndex(k, false)){
                            Drawable drawable = mRecentTaskDescriptions.get(i).getThumbnail();
                            subPanelView[k-1].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));    
                        }
                    }
                }
            }
        }
        
        
        if(3 == sbiu.getTotalWindows()){
            Configuration config = getResources().getConfiguration();
            boolean portMode = (config.orientation == Configuration.ORIENTATION_PORTRAIT);
            if(portMode){
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit){
                    viewSpacing[0].setVisibility(View.VISIBLE);
                    if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() != 0){
                        for(int i = 0; i<mRecentTaskDescriptions.size(); i++){
                            if(mRecentTaskDescriptions.get(i).taskId == sbiu.getTopTaskIdByIndex(4, true)){
                                Drawable drawable = mRecentTaskDescriptions.get(i).getThumbnail();
                                subPanelView[3].setVisibility(View.VISIBLE);
                                subPanelView[3].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));    
                                subPanelView[2].setVisibility(View.GONE);
                            }
                        }
                    }
                    //subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    if(DEBUG) Log.v("Tom","StackBoxInfoUtils.WinDisplayMode.ThreePrTpSplit");
                }else{
                    viewSpacing[2].setVisibility(View.VISIBLE);
                }
            }else{
                if(sbiu.getDisplayMode(portMode) == StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit){
                    // show subpanelview[3] background
                    viewSpacing[0].setVisibility(View.VISIBLE);
                    if(null != mRecentTaskDescriptions && mRecentTaskDescriptions.size() != 0){
                    for(int i = 0; i<mRecentTaskDescriptions.size(); i++){
                        if(DEBUG) Log.v("Tom","mRecentTaskDescriptions.get(i).taskId == " + mRecentTaskDescriptions.get(i).taskId);
                        if(DEBUG) Log.v("Tom","sbiu.getTopTaskIdByIndex(4, false) == " + sbiu.getTopTaskIdByIndex(4, false));

                            if(mRecentTaskDescriptions.get(i).taskId == sbiu.getTopTaskIdByIndex(4, false)){
                                Drawable drawable = mRecentTaskDescriptions.get(i).getThumbnail();
                                subPanelView[3].setVisibility(View.VISIBLE);
                                subPanelView[3].setImageDrawable(drawableStacked(drawable,getResources().getDrawable(R.drawable.recents_previewin_tp_bg)));
                                subPanelView[2].setVisibility(View.GONE);
                            }
                        }
                    }
                    //subPanelView[2].setImageDrawable(getResources().getDrawable(R.drawable.recents_previewin_ntp_bg));
                    if(DEBUG) Log.v("Tom","StackBoxInfoUtils.WinDisplayMode.ThreeLdLtSplit");
                }else{
                    viewSpacing[2].setVisibility(View.VISIBLE);
                }
            }
        }
        //changWinBackDrawable(sbiu.getTotalWindows());
    }

    public class TouchPanelViewSwitchListener implements View.OnTouchListener {
        //private ImageButton mImgBtn;
        private int j = -1;
        private boolean isInside = false;
        private int[] mTempPoint = new int[2];
        
        public TouchPanelViewSwitchListener(int  i) {
            j = i;
        }

        private boolean pointInside(float x, float y, View v) {
            v.getLocationOnScreen(mTempPoint);
            return x >= mTempPoint[0] && x < (mTempPoint[0] + v.getWidth()) && y >= mTempPoint[1] && y < (mTempPoint[1]+v.getHeight());
        }

        public boolean onTouch(View v, MotionEvent ev) {

            final int action = ev.getAction();
            switch(action){
            case MotionEvent.ACTION_DOWN:
                upDateViewByTag(v,true,false);
                isInside = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if(!pointInside(ev.getRawX(),ev.getRawY(),v)){
                    upDateViewByTag(v,false,false);
                    isInside = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if(DEBUG) Log.v("Tom","Action_up" + " isInside == " + isInside);
                upDateViewByTag(v,false,false);
                if(isInside){
                    StackBoxInfoUtils sbiu = new StackBoxInfoUtils();
                    //enter multiwindow and setfocus
                    if(j < sbiu.getTotalWindows()){
                        try {
                            ActivityManagerNative.getDefault().showMultiWindow();
                            if (DEBUG) Slog.d(TAG, "no need to show multi window, just toggle recent");
                        } catch (RemoteException e) {
                            Slog.w(TAG, "unexpected exception when showing multi window", e);
                        }
                    }else
                        dismissAndGoBack();
                    //here,we will modify 
                }
                break;

            default:
                break;
            }
            return true;
        }
    }

    public void prepareDragDrop(int taskId,OnDragListener myDrageListener){
        for(int i = 0; i< subPanelView.length; i++){
            subPanelView[i].setTag(PANEL_EMPTY);
            subPanelView[i].setOnDragListener(myDrageListener);
        }
        if(mRecentsPanelIndicator != null) {
            mRecentsPanelIndicator.setVisibility(View.VISIBLE);
            Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.lenovo_status_bar_recents_preview_enter);
            animation.reset();
            mRecentsPanelIndicator.setAnimation(animation);
        }
    }

    static boolean bSwitch=false;
//MultiWindow NJ lenovo liming11 add 2013-12-06 end

    public void handleLongPress(
            final View selectedView, final View anchorView, final View thumbnailView) {
        if(mPopup != null) {
            mPopup.dismiss();
        }
        //thumbnailView.setSelected(true);
        ViewHolder holder = (ViewHolder) selectedView.getTag();
        TaskDescription ad = holder.taskDescription;

        /*Begin,Lenovo-sw Tom_liming11 add 2014-02-12, change preview and modify the prompt when drag recent task*/
        if(ad != null){
            if(!appInWhiteList(ad.packageName)){
                if (DEBUG) Log.v(TAG, "enter ad.packageName == " + ad.packageName);
                isInWhiteList = false;
                showPanelIndicator();
            }else{
                if (DEBUG) Log.v(TAG, "enter ad.packageName == " + ad.packageName);
                isInWhiteList = true;
                showPanelIndicator();
                showPanelDraging();
            }
            prepareDragDrop(ad.taskId,recentAppDrageListener);
            ClipData data = ClipData.newPlainText("dot", "Dot : " + thumbnailView.toString());
            thumbnailView.startDrag(data,new DragShadowBuilder(thumbnailView), (Object)selectedView, 0);
        }
        /*End,Lenovo-sw Tom_liming11 add 2014-02-12,  change preview and modify the prompt when drag recent task*/
    }
}
