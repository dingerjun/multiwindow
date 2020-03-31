package com.android.systemui.splitbar;

import android.app.ActivityManager.StackBoxInfo;
import android.app.ActivityManager.StackInfo;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.WindowManager;

import java.util.ArrayDeque;
import java.util.List;

import static com.android.server.wm.StackBox.SPLIT_SPACE;

public class StackBoxInfoUtils {
    static final String TAG = "StackBoxInfoUtils";
    boolean DBG = false;
    private int HALF_POINT_WIDTH = (int)(FloatingWindowService.CENTER_POINT_WIDTH*0.5);
    public int mDockWindowStackId;
    public int mLeftDockWindowStackId;
    public int mNewWindowsplitPolicy;

    StackBoxInfo homeStackBoxInfo = null;
    StackBoxInfo appStackBoxInfo = null;
    SparseArray<Rect> mIdToRect = new SparseArray<Rect>();
    SparseArray<String> mTaskId2Name = new SparseArray<String>();
    SparseArray<StackInfo> mStackID2Info = new SparseArray<StackInfo>();

    static final int POP_UP_MENU_NUM = 8;
    static final int DETECT_OFFSET = 80;
    static final int MAX_WIN_NUM = 4;

    /*Lenovo sw begin, xieqiong2 2014.1.16 BLADEFHD-283*/
    static final int CloseMenu[][]
                    = {{0,0,0,0,0,0,0,0},
                       {1,0,0,0,1,0,0,0},
                       {1,0,0,0,1,0,0,0},
                       {0,1,0,0,1,0,0,1},
                       {1,0,0,1,0,1,0,0},
                       {0,1,0,0,1,0,0,1},
                       {1,0,0,1,0,1,0,0},
                       {0,1,0,1,0,1,0,1},
                       {0,1,0,1,0,1,0,1}};

    //Definition for all the window display mode
    public enum WinDisplayMode {
       One,
       TwoLd,
       TwoPr,
       ThreeLdLtSplit,  //three windows in landscape with 2 windows in the left, 1 window in the right.
       ThreeLdRtSplit,
       ThreePrTpSplit,  //three windows in portrait with 2 windows in the top, 1 window in the bottom.
       ThreePrBtSplit,
       FourLd,
       FourPr,
       Invalid
    }
     /*Lenovo sw end, xieqiong2 2014.1.16 BLADEFHD-283*/

    // constructor to get stackbox Infos
    public StackBoxInfoUtils() {
        mDockWindowStackId = -1;
        mNewWindowsplitPolicy = -1;
        appStackBoxInfo = null;
        homeStackBoxInfo = null;
        try {
            List<StackBoxInfo> sbis = ActivityManagerNative.getDefault()
                .getStackBoxes();
            if (sbis.size() > 1) {
                for (StackBoxInfo stackBoxInfo : sbis) {
                    /*Begin,Lenovo sw, Tom-liming11 2014.03.20, null exception*/
                    if (stackBoxInfo != null && stackBoxInfo.stackId != 0) {
                    /*End,Lenovo sw, Tom-liming11 2014.03.20, null exception*/
                        appStackBoxInfo = stackBoxInfo;
                        //break; // we may need to consider multiple user later
                        // instead of break
                    } else {
                        homeStackBoxInfo = stackBoxInfo;
                    }
                }
            }

            if (appStackBoxInfo != null) {
                collectAppStackInfo(appStackBoxInfo);
                visitStackBoxes(appStackBoxInfo);
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getTotalWindows() {
        return mIdToRect.size();
    }
    
    public SparseArray<Rect> getIdToRect() {
        return mIdToRect;
    }

    public int getStackIdfromPoint(int x, int y) {
        for (int i = 0; i < mIdToRect.size(); i++) {
            int key = mIdToRect.keyAt(i);
            Rect rect = mIdToRect.get(key);
            if (rect.contains(x, y)) {
                return key;
            }
        }
        return -1;
    }

    public int getTaskIdFromNameInfo(String taskName){
        for (int i = 0; i < mTaskId2Name.size(); i++) {
            int key = mTaskId2Name.keyAt(i);
            String name = mTaskId2Name.get(key);
            if (taskName.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return -1;
    }
    
    public StackInfo getStackFromStackId(int stackId){
        return mStackID2Info.get(stackId);
    }

    public int getFocusedStack() {
        int focusedStack = -1;
        try {
            focusedStack = ActivityManagerNative.getDefault().getFocusedStack2();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return focusedStack;
    }

    public int addNewWindow() {
        return addNewWindow(false);
    }

    public int addNewWindow(boolean bLeft) {
        int newStackId = -1;
        if (getTotalWindows() > 0) {
            try {
                newStackId = ActivityManagerNative.getDefault().createStack(-1,
                        (bLeft==true && mLeftDockWindowStackId!=-1)?mLeftDockWindowStackId:mDockWindowStackId, mNewWindowsplitPolicy,// StackBox.TASK_STACK_GOES_ABOVE,
                        mNewWindowsplitPolicy==3?0.5f:0.36f);
                ActivityManagerNative.getDefault().setFocusedStack2(newStackId);
                return newStackId;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return newStackId;
    }

    public int setTargetWindow(int index, boolean portMode) {
        int newStackId = getStackIdByIndex(index, portMode);

        try {
            ActivityManagerNative.getDefault().setFocusedStack2(newStackId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return newStackId;
    }

    /*Lenovo sw begin, xieqiong2 2014.1.16 BLADEFHD-283*/
    public WinDisplayMode getDisplayMode(boolean portMode) {
        int num = getTotalWindows();
        WinDisplayMode mode = WinDisplayMode.Invalid;
        if ((num <= 0) || (num > MAX_WIN_NUM)) {
            if(DBG) Log.d(TAG, "invalid display mode!");
        } else {
          switch (num){
            case 1:
              mode = WinDisplayMode.One;
              break;
            case 2:
              mode = portMode?WinDisplayMode.TwoPr:WinDisplayMode.TwoLd;
              break;
            case 3:
              Rect totalScreen = homeStackBoxInfo.bounds;
              int screenWidth = totalScreen.width();
              int screenHeight = totalScreen.height();
              int id1 = getStackIdfromPoint(DETECT_OFFSET, DETECT_OFFSET);
              int id2 = getStackIdfromPoint(DETECT_OFFSET, screenHeight - DETECT_OFFSET);
              int id3 = getStackIdfromPoint(screenWidth - DETECT_OFFSET,DETECT_OFFSET);
              if(portMode){
                 mode = (id1==id3)?WinDisplayMode.ThreePrBtSplit:WinDisplayMode.ThreePrTpSplit;
              } else {
                 mode = (id1==id2)?WinDisplayMode.ThreeLdRtSplit:WinDisplayMode.ThreeLdLtSplit;
              }
              break;
            case 4:
              mode = portMode?WinDisplayMode.FourPr:WinDisplayMode.FourLd;
              break;
            default:
              break;
           }
        }
        if(DBG) Log.d(TAG, "getDisplayMode = " + mode);
        return mode;
    }
    /*Lenovo sw end, xieqiong2 2014.1.16 BLADEFHD-283*/

    // This function is for 1-4 windows only!hard coded!!!
    // 1,2,3,4 as parameter
    // start from 1!
    public int getStackIdByIndex(int index, boolean portMode) {
        WinDisplayMode mode = getDisplayMode(portMode);
        if (mode == WinDisplayMode.Invalid || index <= 0 || index > MAX_WIN_NUM) {
            return -1;
        }
        Rect totalScreen = homeStackBoxInfo.bounds;
        int screenWidth = totalScreen.width();
        int screenHeight = totalScreen.height();

        switch(index) {
            case 1:
                return getStackIdfromPoint(DETECT_OFFSET, DETECT_OFFSET);
            case 2:
                switch (mode) {
                     case TwoPr:
                          return getStackIdfromPoint(DETECT_OFFSET, screenHeight - DETECT_OFFSET);
                     case TwoLd:
                          return getStackIdfromPoint(screenWidth - DETECT_OFFSET,DETECT_OFFSET);
                     case ThreePrBtSplit:
                     case ThreePrTpSplit:
                     case FourPr:
                          return getStackIdfromPoint(DETECT_OFFSET,screenHeight - DETECT_OFFSET);
                     case ThreeLdLtSplit:
                     case ThreeLdRtSplit:
                     case FourLd:
                          return getStackIdfromPoint(screenWidth - DETECT_OFFSET, DETECT_OFFSET);
                 }
                 break;
             case 3:
                switch (mode) {
                     case ThreeLdRtSplit:
                     case ThreePrBtSplit:
                     case FourPr:
                     case FourLd:
                          return getStackIdfromPoint(screenWidth - DETECT_OFFSET,screenHeight - DETECT_OFFSET);
                }
                break;
              case 4:
                switch (mode) {
                     case ThreePrTpSplit:
                     case FourPr:
                          return getStackIdfromPoint(screenWidth - DETECT_OFFSET, DETECT_OFFSET);
                     case ThreeLdLtSplit:
                     case FourLd:
                          return getStackIdfromPoint(DETECT_OFFSET, screenHeight - DETECT_OFFSET);
                }
        }
        return -1;
    }

    class CombinedStackBoxInfo {
        StackBoxInfo mStackBoxNode;
        boolean oddNote;

        CombinedStackBoxInfo(StackBoxInfo node, boolean odd) {
            mStackBoxNode = node;
            oddNote = odd;
        }
    }

    private void visitStackBoxes(StackBoxInfo stackBoxInfoNode) {
        if (stackBoxInfoNode != null) {
            if (stackBoxInfoNode.children != null) {
                visitStackBoxes(stackBoxInfoNode.children[0]);
                visitStackBoxes(stackBoxInfoNode.children[1]);
            } else {
                // target reached, do something
                mIdToRect
                    .put(stackBoxInfoNode.stackId, stackBoxInfoNode.bounds);
                mStackID2Info
                    .put(stackBoxInfoNode.stackId,stackBoxInfoNode.stack);
                for (int i=0;i<stackBoxInfoNode.stack.taskIds.length;i++){
                    mTaskId2Name.put(stackBoxInfoNode.stack.taskIds[i], stackBoxInfoNode.stack.taskNames[i]);
                    Log.d(TAG, "taskid:" + stackBoxInfoNode.stack.taskIds[i] + ",taskname:" + stackBoxInfoNode.stack.taskNames[i]);
                }
            }
        }
    }

    /*Lenovo sw begin, xieqiong2 2014.1.16 BLADEFHD-283*/
    public int[] getSpiltWinCenterXY(boolean portMode) {
        int touchXY[] = {0, 0};
        StackBoxInfo tempSb = appStackBoxInfo;
        if (tempSb == null){
            return touchXY;
        }
        int splitSpace = (int)(SPLIT_SPACE*0.5);
        WinDisplayMode mode = getDisplayMode(portMode);

        /*Lenovo sw add, xieqiong2 2014.1.28 BLADEFHD-283, to handle the 3 windows special cases*/
        if(mode == WinDisplayMode.ThreePrTpSplit || mode == WinDisplayMode.ThreeLdLtSplit) {
            if(tempSb.children != null && tempSb.children[0].children != null) {
                touchXY[0] = tempSb.children[0].children[0].bounds.width() + splitSpace - HALF_POINT_WIDTH;
                touchXY[1] = tempSb.children[0].children[0].bounds.height() + splitSpace - HALF_POINT_WIDTH;
            }
        } else {
            for (;;){
                if(DBG) Log.d(TAG, "stackbox " + "id " + tempSb.stackBoxId + ", stackBoxId" + tempSb.stackBoxId + " = rect = "+ tempSb.bounds.left + " " + tempSb.bounds.right + " " + tempSb.bounds.top + " " + tempSb.bounds.bottom);
                if (tempSb.children == null) {
                     touchXY[0] = (tempSb.bounds.left == homeStackBoxInfo.bounds.left)
                                    ?0:(homeStackBoxInfo.bounds.width()-tempSb.bounds.width()-splitSpace-HALF_POINT_WIDTH);
                     touchXY[1] = (tempSb.bounds.top == homeStackBoxInfo.bounds.top)
                                    ?0:(homeStackBoxInfo.bounds.height()-tempSb.bounds.height()-splitSpace-HALF_POINT_WIDTH);
                     Log.d(TAG, "home height = " + homeStackBoxInfo.bounds.height());
                     break;
                } else {
                     tempSb = tempSb.children[1];
                }
            }
        }

        //special handle for 2 window cases
        if((touchXY[0] == 0) && (touchXY[1] > 0))
              touchXY[0] = (int)(homeStackBoxInfo.bounds.width()*0.5 - HALF_POINT_WIDTH);
        if((touchXY[0] > 0) && (touchXY[1] == 0))
              touchXY[1] = (int)(homeStackBoxInfo.bounds.height()*0.5 - HALF_POINT_WIDTH);
        if(DBG) Log.d(TAG, "x=" + touchXY[0] + ", y=" + touchXY[1]);
        return touchXY;
    }
    
    public void refreshAfterDrag(int x, int y, boolean portMode) {
        float weightX, weightY;
        weightX = weightY = 0f;

        if(DBG) Log.d(TAG, "refreshAfterDrag, homestack = " + homeStackBoxInfo);
        if(DBG) Log.d(TAG, "refreshAfterDrag, appStackBoxInfo = " + appStackBoxInfo);
        if(DBG) Log.d(TAG , "touch x,y = " + x + "," + y);
        weightX = (float)(x) / (float)(homeStackBoxInfo.bounds.width());
        if(DBG) Log.d(TAG, "weightX = " + weightX);
        weightY =  (float)(y) / (float)(homeStackBoxInfo.bounds.height());
        if(DBG) Log.d(TAG, "homeStackBoxInfo.bounds.height() = " + homeStackBoxInfo.bounds.height());
        if(DBG) Log.d(TAG, "weightY = " + weightY);
        try {
            if  (appStackBoxInfo.children != null) {
                ActivityManagerNative.getDefault().resizeStackBox(appStackBoxInfo.children[0].stackBoxId, portMode?weightY:weightX);
                if (appStackBoxInfo.children[0].children != null ) {
                   ActivityManagerNative.getDefault().resizeStackBox(appStackBoxInfo.children[0].children[0].stackBoxId, portMode?weightX:weightY);
                }
                if (appStackBoxInfo.children[1].children != null) {
                   ActivityManagerNative.getDefault().resizeStackBox(appStackBoxInfo.children[1].children[0].stackBoxId, portMode?weightX:weightY);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void setLockFocusSwitch(boolean bLock){
        try {
                ActivityManagerNative.getDefault().setLockFocusSwitch(bLock);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public SparseArray<Rect> getMovePanelXY(int x, int y, boolean portMode){
        SparseArray<Rect> moveRect = new SparseArray<Rect>();
        int x1, x2, x3, x4, y1, y2, y3, y4;
        int splitSpace = (int)(SPLIT_SPACE*0.5);

        x1 = homeStackBoxInfo.bounds.left;
        x2 = x1 + x - splitSpace;
        x3 = x2 + SPLIT_SPACE;
        x4 = homeStackBoxInfo.bounds.right;
        y1 = homeStackBoxInfo.bounds.top;
        y2 = y1 + y - splitSpace;
        y3 = y2 + SPLIT_SPACE;
        y4 = homeStackBoxInfo.bounds.bottom;

        if  (appStackBoxInfo.children != null) {
                if (appStackBoxInfo.children[0].children != null ) {
                   Rect tmpBound1 = new Rect(x1,y1,x2,y2);
                   moveRect.put(appStackBoxInfo.children[0].children[0].stackId, tmpBound1);
                   Rect tmpBound2 = portMode?new Rect(x3,y1,x4,y2):new Rect(x1,y3,x2,y4);
                   moveRect.put(appStackBoxInfo.children[0].children[1].stackId, tmpBound2);        
                } else {
                   Rect tmpBound = portMode?new Rect(x1,y1,x4,y2):new Rect(x1,y1,x2,y4);
                   moveRect.put(appStackBoxInfo.children[0].stackId, tmpBound);
                }
                if (appStackBoxInfo.children[1].children != null) {
                   Rect tmpBound1 = portMode?new Rect(x1,y3,x2,y4):new Rect(x3,y1,x4,y2);
                   moveRect.put(appStackBoxInfo.children[1].children[0].stackId, tmpBound1);
                   Rect tmpBound2 = new Rect(x3,y3,x4,y4);
                   moveRect.put(appStackBoxInfo.children[1].children[1].stackId, tmpBound2);
                } else {
                   Rect tmpBound = portMode?new Rect(x1,y3,x4,y4):new Rect(x3,y1,x4,y4);
                   moveRect.put(appStackBoxInfo.children[1].stackId, tmpBound);
                }
        }
        if(DBG) Log.d(TAG, "moveRect = " + moveRect);
        return moveRect;
    }

    int convertIdfromCloseMenuId(int i) {
           switch (i) {
               case 0:
               case 1:
                  return 1;
               case 3:
               case 4:
                  return 2;
               case 5:
                  return 3;
               case 7:
                  return 4;
               default:
                  return -1;
           }
    }

    public int[] getCloseMenuInfo(boolean portMode) {
        int[] closeMenu = new int[POP_UP_MENU_NUM];
        WinDisplayMode mode = getDisplayMode(portMode);
        for(int i = 0; i < POP_UP_MENU_NUM; i++){
           int value = CloseMenu[mode.ordinal()][i];
           if(value == 1){ //find the close menu position
                int index = convertIdfromCloseMenuId(i);
                closeMenu[i] = getStackIdByIndex(index, portMode);
           }
        }
        for(int j=0; j<8; j++) {
           if(DBG) Log.d(TAG, "closeMenu[" + j + "]=" + closeMenu[j]);
        }
        return portMode ? formartCloseMeuuInfo(closeMenu) : closeMenu;
    }

    private int[] formartCloseMeuuInfo(int[] closeMenu) {
        int[] temp = new int[POP_UP_MENU_NUM];
        temp[0] = closeMenu[2];
        temp[1] = closeMenu[1];
        temp[2] = closeMenu[0];
        temp[3] = closeMenu[7];
        temp[4] = closeMenu[6];
        temp[5] = closeMenu[5];
        temp[6] = closeMenu[4];
        temp[7] = closeMenu[3];
        return temp;
    }

    public boolean isTaskInStack(int taskId, int stackId) {
        StackInfo stack = mStackID2Info.get(stackId);
        for (int i = 0; i < stack.taskIds.length; i++){
           if(taskId == stack.taskIds[i]) {
               return true;
           }
        }
        return false;
    }

    public int getStackTaskSizeByStackId(int stackId){
        return mStackID2Info.get(stackId).taskIds.length;
    }

    public int getStackIdByTaskId(int taskId) {
        for(int i = 0; i < mStackID2Info.size(); i++) {
           int stackId = mStackID2Info.keyAt(i);
           if(isTaskInStack(taskId, stackId))
               return stackId;
        }
        return -1;
    }

    public int getTopTaskIdByIndex(int index, boolean portMode) {
        int stackId = getStackIdByIndex(index, portMode);
        if(stackId >= 0) {
            int length = mStackID2Info.get(stackId).taskIds.length;
            if (length > 0) //in case the stack has no task but stack is still there!
               return mStackID2Info.get(stackId).taskIds[length-1];
        }
        return -1;
    }

    public static int getStatusBarHeight (Context context) {
        return context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
    }
    /*Lenovo sw end, xieqiong2 2014.1.16 BLADEFHD-283*/

    private void collectAppStackInfo(StackBoxInfo appStackBoxInfo) {
        mDockWindowStackId = -1;
        mLeftDockWindowStackId = -1;
        mNewWindowsplitPolicy = -1;
        boolean bPreviousLevelCategory = true;
        if (appStackBoxInfo == null) {
            return;
        }
        // policy: 0: 4, 1:3
        ArrayDeque<CombinedStackBoxInfo> queue = new ArrayDeque<CombinedStackBoxInfo>();
        CombinedStackBoxInfo tmpInfo = new CombinedStackBoxInfo(
                appStackBoxInfo, true);
        queue.add(tmpInfo);
        while (queue.isEmpty() == false) {
            CombinedStackBoxInfo node = queue.remove();

            if (bPreviousLevelCategory != node.oddNote){
                bPreviousLevelCategory = node.oddNote;
                if (node.mStackBoxNode.children == null) {
                    mDockWindowStackId = node.mStackBoxNode.stackBoxId;
                } else {
                    if (node.mStackBoxNode.children[1] != null) {
                        queue.add(new CombinedStackBoxInfo(
                                    node.mStackBoxNode.children[1], !node.oddNote));
                    }
                    if (node.mStackBoxNode.children[0] != null) {
                        queue.add(new CombinedStackBoxInfo(
                                    node.mStackBoxNode.children[0], !node.oddNote));
                    }
                }
            } else {
                if (node.mStackBoxNode.children == null) {
                    mLeftDockWindowStackId = node.mStackBoxNode.stackBoxId;
                    if (mDockWindowStackId==-1){
                        mDockWindowStackId = mLeftDockWindowStackId;
                        mLeftDockWindowStackId = -1;
                    }
                } else {
                    if (node.mStackBoxNode.children[1] != null) {
                        queue.add(new CombinedStackBoxInfo(
                                    node.mStackBoxNode.children[1], !node.oddNote));
                    }
                    if (node.mStackBoxNode.children[0] != null) {
                        queue.add(new CombinedStackBoxInfo(
                                    node.mStackBoxNode.children[0], !node.oddNote));
                    }
                }

                if ((mDockWindowStackId!=-1) || (mLeftDockWindowStackId!=-1)){
                    mNewWindowsplitPolicy = (node.oddNote)?5:3;
                    return;
                }
            }

        }
        return;
    }

}
