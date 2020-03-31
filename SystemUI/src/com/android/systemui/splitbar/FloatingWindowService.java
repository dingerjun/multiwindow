package com.android.systemui.splitbar;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.StackBoxInfo;
import android.app.ActivityManager.StackInfo;
import android.app.ActivityManagerNative;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.IBinder;
import android.util.FloatMath;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.systemui.R;
import android.util.SparseArray;

import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.splitbar.view.animation.InOutAnimation;
import com.android.systemui.splitbar.view.animation.MenuButtonAnimation;
import static com.android.server.wm.StackBox.SPLIT_SPACE;

public class FloatingWindowService extends Service {

    public static String TAG = "FloatingWindowService";
    boolean DBG = false;
    public static String ADD_SPLIT_UI = "addSplitUI";
    public static int CENTER_POINT_WIDTH = 70;  //the width of the round touch circle
        /*Lenovo sw, xieqiong2 add 2014.1.28 BLADEFHD-283*/
        private static int MOVE_POINT_RADIUS = 50;  //the radius of the dragging circle
    private static float SPLIT_MIN = 0.25f;
    private static float SPLIT_MAX = 0.75f;
    private static int PADDING = 30;
    private static int MENU_ANM_RADIUS = 100;

    // Flag to show drag area.
    private static WindowManager wm = null;
   private StackBoxInfoUtils sbiUtils;

    // current configurations
    private int mStackWindowNum = 0;
    private int mStatusBarHight = 0;
    private int mTotalHeight = 0;
    private int mTotalWidth = 0;
    private boolean bPortraitMode = false;
    private int DRAG_H_MIN = 0;
    private int DRAG_H_MAX = 0;
    private int DRAG_W_MIN = 0;
    private int DRAG_W_MAX = 0;
    private Point mCurrentPoint = null;
    private Point mOutSize = null;

    SparseArray<Rect> mIdToRect;

    private View mPointView = null;
    private ImageView mDragView = null;
    private static WindowManager.LayoutParams mSplitLayoutParams = null;
    private static WindowManager.LayoutParams mDragLayoutParams = null;

    private boolean mViewAdded = false;

    private boolean mCloseButtonShowing;
    private ViewGroup mCloseButtonsWrapper;
    private ViewGroup mMenuWrapper;
    private ImageView mSplitPoint;
    
    final class H extends Handler{
        public static final int REPORT_SPLITBAR_CHANGE = 1;

        public static final int REPORT_MENU_DISMISS = 2;

        public static final int REPORT_SPLITVIEW_REFRESH = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case REPORT_SPLITBAR_CHANGE: {
                addorUpdateView();
            }
                break;
            case REPORT_MENU_DISMISS: {
                dismissMenuButtons();
            }
                break;
            case REPORT_SPLITVIEW_REFRESH: {
                if(DBG) Log.d(TAG, "receive REPORT_SPLITVIEW_REFRESH.");
                initialPosition();
                //Lenovo sw, xieqiong2 2014.2.28  BLADEFHD-2876: to fix the position while the menu is opened.
                mSplitLayoutParams.x = mCurrentPoint.x - (mCloseButtonShowing?MENU_ANM_RADIUS:0);
                mSplitLayoutParams.y = mCurrentPoint.y - (mCloseButtonShowing?MENU_ANM_RADIUS:0);
                if (mViewAdded) {
                    wm.updateViewLayout(mPointView, mSplitLayoutParams);
                    mPointView.setOnSystemUiVisibilityChangeListener(mOnSystemUiVisibilityChangeListener);
                }
            }
                break;
            default:
                break;
            }
        }
    }
    final H mH = new H();
    private void timedDismiss(){
        mH.removeMessages(H.REPORT_MENU_DISMISS);
        mH.sendEmptyMessageDelayed(H.REPORT_MENU_DISMISS, 3000);
    }

    private void timedTrigger(){
        mH.removeMessages(H.REPORT_SPLITBAR_CHANGE);
        mH.sendEmptyMessageDelayed(H.REPORT_SPLITBAR_CHANGE, 500);
    }

    private void timedRefresh(){
        mH.removeMessages(H.REPORT_SPLITVIEW_REFRESH);
        mH.sendEmptyMessageDelayed(H.REPORT_SPLITVIEW_REFRESH, 500);
    }

    private void vibrate() {
        android.os.Vibrator vib = (android.os.Vibrator) getApplicationContext()
                .getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(50);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getApplicationContext().getSystemService(
                Context.WINDOW_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        boolean add = false;
        if(intent != null){
            add = intent.getBooleanExtra(ADD_SPLIT_UI, false);
        }
        if(DBG) Log.d(TAG, "onStart: intent ADD_SPLIT_UI = " + add);
        if (add) {
            addorUpdateView();
        } else {
            removeViews();
        }
    }

    // mConfigurationReceiver
    BroadcastReceiver mConfigurationReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                timedTrigger();
            }
        }
    };

    /**
     * onConfigurationChanged
     */
    private void getCurrentConfiguration() {
        // get window width
        if (mOutSize == null) {
            mOutSize = new Point();
        }
        wm.getDefaultDisplay().getSize(mOutSize);

        int curRotation = wm.getDefaultDisplay().getRotation();
        if (curRotation == Surface.ROTATION_0 || curRotation == Surface.ROTATION_180){
            bPortraitMode = false;
        } else {
            bPortraitMode = true;
        }

        int statusBarHeight =  StackBoxInfoUtils.getStatusBarHeight(getApplicationContext());
        mTotalWidth = mOutSize.x;
        /*Lenovo sw, xieqiong2 modify 2014.1.16 BLADEFHD-283*/
        mTotalHeight = mOutSize.y - statusBarHeight;

        DRAG_H_MIN = (int) (mTotalHeight * SPLIT_MIN);
        DRAG_H_MAX = (int) (mTotalHeight * SPLIT_MAX);
        DRAG_W_MIN = (int) (mTotalWidth * SPLIT_MIN);
        DRAG_W_MAX = (int) (mTotalWidth * SPLIT_MAX);
    }

    View.OnSystemUiVisibilityChangeListener mOnSystemUiVisibilityChangeListener
           = new View.OnSystemUiVisibilityChangeListener(){
        public void onSystemUiVisibilityChange(int visibility) {
            timedRefresh();
        }
    };

    /**
     * 判断位置是否合法，并返回合法位置
     * @param rawX
     * @param rawY
     * @return
     */
    private Point getAdjustedPosition(int rawX, int rawY) {
        Point rp = new Point();
        if (rawX < DRAG_W_MIN) {
            rp.x = DRAG_W_MIN;
        } else if (rawX > DRAG_W_MAX) {
            rp.x = DRAG_W_MAX;
        } else {
            rp.x = rawX;
        }

        if (rawY < DRAG_H_MIN) {
            rp.y = DRAG_H_MIN;
        } else if (rawY > DRAG_H_MAX) {
            rp.y = DRAG_H_MAX;
        } else {
            rp.y = rawY;
        }

        return rp;
    }

    /**
     * 初始化点的位置
     */
    private void initialPosition() {
        sbiUtils = new StackBoxInfoUtils();
        mStackWindowNum = sbiUtils.getTotalWindows();
        mIdToRect = sbiUtils.getIdToRect();
        int[] point = sbiUtils.getSpiltWinCenterXY(bPortraitMode);
        if (mCurrentPoint == null) {
            mCurrentPoint = new Point();
        }
        mCurrentPoint.x = point[0];
        mCurrentPoint.y = point[1];

    }

    private void createFloatView() {
        mPointView = LayoutInflater.from(this).inflate(R.layout.split_point, null);
        mPointView.setOnSystemUiVisibilityChangeListener(mOnSystemUiVisibilityChangeListener);

        mSplitLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, LayoutParams.TYPE_PHONE,
                LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT);
        mSplitLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mSplitLayoutParams.format = PixelFormat.RGBA_8888;

        mSplitLayoutParams.width = CENTER_POINT_WIDTH;
        mSplitLayoutParams.height = CENTER_POINT_WIDTH;
        mSplitLayoutParams.x = mCurrentPoint.x;
        mSplitLayoutParams.y = mCurrentPoint.y;
        mSplitLayoutParams.windowAnimations = 0;
        // disable animation cause it looks ugly ...
        mSplitLayoutParams.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;

        mCloseButtonsWrapper = (ViewGroup) mPointView.findViewById(R.id.close_buttons_wrapper);
        filterCloseButtonInfo(mCloseButtonsWrapper);

        mMenuWrapper = (ViewGroup) mPointView.findViewById(R.id.menu_wrapper);
        filterMenuInfo(mMenuWrapper);

        mSplitPoint = (ImageView) mPointView.findViewById(R.id.split_point);
        for (int i = 0; i < mCloseButtonsWrapper.getChildCount(); i++) {
            mCloseButtonsWrapper.getChildAt(i).setOnTouchListener(wrapperTouchListener);
            mCloseButtonsWrapper.getChildAt(i).setOnClickListener(
                new OnClickListener() {
                    public void onClick(View v) {
                        dismissMenuButtons();
                        int stackid = (Integer)v.getTag();
                        removeStack(stackid);
                        timedTrigger();
                    }

                    private void removeStack(int stackId) {
                        try {
                            ActivityManagerNative.getDefault().removeStack(stackId, false);
                        } catch (RemoteException e) {
                            Log.e(TAG, "", e);
                        }
                    }
                });
        }

        for (int i = 0; i < mMenuWrapper.getChildCount(); i++) {
            mMenuWrapper.getChildAt(i).setOnTouchListener(wrapperTouchListener);
            mMenuWrapper.getChildAt(i).setOnClickListener(
                    new OnClickListener() {
                        public void onClick(View v) {
                            dismissMenuButtons();
                            switch (v.getId()) {
                            case R.id.menu_add_first:
                            case R.id.menu_add_second:
                                Intent intent = new Intent(RecentsActivity.TOGGLE_RECENTS_INTENT);
                                intent.setClassName("com.android.systemui","com.android.systemui.recent.RecentsActivity");
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                getApplicationContext().startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                                break;
                            case R.id.menu_max_first:
                            case R.id.menu_max_second:
                                MaxFocusedWindow();
                                break;
                            }
                        }
                    });
        }

        mSplitPoint.setOnTouchListener(pointViewTouchListener);

        wm.addView(mPointView, mSplitLayoutParams);
        mStatusBarHight = StackBoxInfoUtils.getStatusBarHeight(getApplicationContext());
    }

    OnTouchListener wrapperTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sbiUtils.setLockFocusSwitch(true);
                    break;
            }
            return false;
        }
    };

    OnTouchListener pointViewTouchListener = new OnTouchListener() {
        boolean bMoved = false;
        float[] temp = new float[2];

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if(DBG) Log.d(TAG, "ACTION_DOWN");
                vibrate();
                bMoved = false;
                temp[0] = event.getX();
                temp[1] = event.getY();
                sbiUtils.setLockFocusSwitch(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if(DBG) Log.d(TAG, "ACTION_MOVE");
                float space = moveSpace(event, temp);
                if (space > 10f) {
                    if(DBG) Log.d(TAG, "space > 10f");
                    dismissMenuButtons();
                    bMoved = true;
                    mCurrentPoint = getAdjustedPosition((int) event.getRawX(),
                            (int) event.getRawY());
                                        /*Lenovo sw, xieqiong2 modify 2014.1.28 BLADEFHD-283*/
                                        if (mDragView == null ) {
                        initDragViews();
                                        }
                    mDragView.invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if(DBG) Log.d(TAG, "ACTION_UP");
                if (!bMoved) {
                    // deal it like a click event
                    toggleMenuButtons();
                } else {
                    bMoved = false;
                    /*Lenovo sw, xieqiong2 to fix the center point display issue 2014.1.28 BLADEFHD-283*/
                    mSplitLayoutParams.x = mCurrentPoint.x - (int)(CENTER_POINT_WIDTH*0.5);
                    mSplitLayoutParams.y = mCurrentPoint.y - (int)(CENTER_POINT_WIDTH*0.5);
                    wm.updateViewLayout(mPointView, mSplitLayoutParams);
                    mPointView.setOnSystemUiVisibilityChangeListener(mOnSystemUiVisibilityChangeListener);
                    stopDragging();
                    sbiUtils.refreshAfterDrag(mCurrentPoint.x,mCurrentPoint.y,bPortraitMode);
                }
                break;
            }
            return true;
        }
    };

    /**
     * refresh float split view
     */
    private void addorUpdateView() {
        if (mViewAdded) {
            removeViews();
        }
        getCurrentConfiguration();
        initialPosition();
        createFloatView();
        stopDragging();
        // register rotations
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mConfigurationReceiver, filter);
        mViewAdded = true;
    }

    /**
     * close float split view
     */
    private void removeViews() {
        mH.removeMessages(H.REPORT_SPLITBAR_CHANGE);
        mH.removeMessages(H.REPORT_MENU_DISMISS);
        mH.removeMessages(H.REPORT_SPLITVIEW_REFRESH);
        if (mViewAdded) {
            wm.removeView(mPointView);
            // unregister rotations
            unregisterReceiver(mConfigurationReceiver);
            mViewAdded = false;
            mCloseButtonShowing = false;
        }
    }

    private void initDragViews() {
        mDragView = new DrawImageView(getApplicationContext());
        mDragView.setImageResource(R.drawable.split_drag_background_drawable);

        mDragLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, LayoutParams.TYPE_PHONE,
                LayoutParams.FLAG_NOT_TOUCH_MODAL
                | LayoutParams.FLAG_NOT_FOCUSABLE
                | LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | LayoutParams.FLAG_ALT_FOCUSABLE_IM
                , PixelFormat.TRANSPARENT);
        mDragLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mDragLayoutParams.format = PixelFormat.RGBA_8888;

        mDragLayoutParams.x = 0;
        mDragLayoutParams.y = 0;
        mDragLayoutParams.width = mOutSize.x;
        mDragLayoutParams.height = mOutSize.y;
        mDragLayoutParams.windowAnimations = 0;

        wm.addView(mDragView, mDragLayoutParams);
        mDragView.setDrawingCacheEnabled(true);
                mDragView.setOnClickListener(new OnClickListener(){

                        @Override
                        public void onClick(View v) {
                            stopDragging();
                            addorUpdateView();
                        }
                });
    }

    private void stopDragging() {
        if (mDragView != null) {
            wm.removeView(mDragView);
            mDragView.setImageDrawable(null);
            mDragView = null;
        }
    }

    private void filterCloseButtonInfo(ViewGroup group) {
        if (group == null) {
            return;
        }
        View[] tempView = new View[8];
        int j = 0;
        int[] closeMenuInfo = sbiUtils.getCloseMenuInfo(bPortraitMode);
        for (int i = 0; i < closeMenuInfo.length; i++) {
            if (closeMenuInfo[i] != 0) {
                group.getChildAt(i).setTag(closeMenuInfo[i]);
            } else {
                tempView[j++] = group.getChildAt(i);
            }
        }
        for (int m = 0; m < j; m++) {
            group.removeView(tempView[m]);
        }
        tempView = null;
    }

    private void filterMenuInfo(ViewGroup group) {
        if (group == null) {
            return;
        }

        int[] closeMenuInfo = sbiUtils.getCloseMenuInfo(bPortraitMode);
        for (int i = 0; i < closeMenuInfo.length; i++) {
            if (closeMenuInfo[i] == 0 && i % 2 == 0) {
                if (i == 0 && closeMenuInfo[4] == 0) {
                    group.removeViewAt(3);
                    group.removeViewAt(1);
                } else {
                    group.removeViewAt(2);
                    group.removeViewAt(0);
                }
                break;
            }
        }
    }

    private void MaxFocusedWindow() {
        int focusedStack = sbiUtils.getFocusedStack();
        Log.d(TAG, "focusedStack:" + focusedStack);
        Rect rect = mIdToRect.get(focusedStack);
        if (rect == null) {
            return;
        }
        if (rect.contains(StackBoxInfoUtils.DETECT_OFFSET, StackBoxInfoUtils.DETECT_OFFSET)) {
            sbiUtils.refreshAfterDrag(DRAG_W_MAX, DRAG_H_MAX, bPortraitMode);
        } else if (rect.contains(StackBoxInfoUtils.DETECT_OFFSET, mTotalHeight - StackBoxInfoUtils.DETECT_OFFSET)) {
            sbiUtils.refreshAfterDrag(DRAG_W_MAX, DRAG_H_MIN, bPortraitMode);
        } else if (rect.contains(mTotalWidth - StackBoxInfoUtils.DETECT_OFFSET, StackBoxInfoUtils.DETECT_OFFSET)) {
            sbiUtils.refreshAfterDrag(DRAG_W_MIN, DRAG_H_MAX, bPortraitMode);
        } else if (rect.contains(mTotalWidth - StackBoxInfoUtils.DETECT_OFFSET, mTotalHeight - StackBoxInfoUtils.DETECT_OFFSET)) {
            sbiUtils.refreshAfterDrag(DRAG_W_MIN, DRAG_H_MIN, bPortraitMode);
        }
        timedRefresh();
    }

    class DrawImageView extends ImageView {

        private Paint paint;

        public DrawImageView(Context context) {
            super(context);
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(Color.rgb(57, 187, 224));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int ra = MOVE_POINT_RADIUS;
            Path path = new Path();
            Point point = new Point();
            Log.d(TAG, "onDraw, mCurrentPoint x, y =" + mCurrentPoint.x + "," + mCurrentPoint.y);
            /*Lenovo sw, xieqiong2 to fix the drag circle display issue 2014.1.28 BLADEFHD-283*/
            point.x = mCurrentPoint.x;
            point.y = mCurrentPoint.y + StackBoxInfoUtils.getStatusBarHeight(getApplicationContext());
            path.moveTo(point.x , point.y);
            path.addCircle(point.x, point.y, ra, Path.Direction.CCW);
            canvas.clipPath(path, Region.Op.XOR);

            float mradius = 5.0f;
            SparseArray<Rect> moveRect = sbiUtils.getMovePanelXY(mCurrentPoint.x, mCurrentPoint.y, bPortraitMode);
            for (int i = 0; i < moveRect.size(); i++) {
                Rect rect = moveRect.get(moveRect.keyAt(i));
                RectF r = new RectF(rect.left, rect.top, rect.right,
                        rect.bottom);
                canvas.drawRoundRect(r, mradius, mradius, paint);
            }

            canvas.drawCircle(point.x, point.y, ra, paint);
        }
    }

    private void toggleMenuButtons() {
        if (!mCloseButtonShowing) {
            mSplitLayoutParams.width = CENTER_POINT_WIDTH + MENU_ANM_RADIUS*2;
            mSplitLayoutParams.height = CENTER_POINT_WIDTH + MENU_ANM_RADIUS*2;
            mSplitLayoutParams.x = mSplitLayoutParams.x - MENU_ANM_RADIUS;
            mSplitLayoutParams.y = mSplitLayoutParams.y - MENU_ANM_RADIUS;
            wm.updateViewLayout(mPointView, mSplitLayoutParams);
            MenuButtonAnimation.startAnimations(
                    this.mCloseButtonsWrapper, InOutAnimation.Direction.IN);
            MenuButtonAnimation.startAnimations(
                    this.mMenuWrapper, InOutAnimation.Direction.IN);
            mCloseButtonShowing = true;
            timedDismiss();
        } else {
            dismissMenuButtons();
        }
    }

    private void dismissMenuButtons() {
        if (mCloseButtonShowing) {
            mSplitLayoutParams.width = CENTER_POINT_WIDTH;
            mSplitLayoutParams.height = CENTER_POINT_WIDTH;
            mSplitLayoutParams.x = mSplitLayoutParams.x + MENU_ANM_RADIUS;
            mSplitLayoutParams.y = mSplitLayoutParams.y + MENU_ANM_RADIUS;
            wm.updateViewLayout(mPointView, mSplitLayoutParams);
            MenuButtonAnimation.startAnimations(
                    this.mCloseButtonsWrapper, InOutAnimation.Direction.OUT);
            MenuButtonAnimation.startAnimations(
                    this.mMenuWrapper, InOutAnimation.Direction.OUT);
            mCloseButtonShowing = false;
            mH.removeMessages(H.REPORT_MENU_DISMISS);
        }
    }

    /** Determine the space when move */
    private float moveSpace(MotionEvent event, float[] temp) {
        float x = event.getX() - temp[0];
        float y = event.getY() - temp[1];
        return FloatMath.sqrt(x * x + y * y);
    }

}
