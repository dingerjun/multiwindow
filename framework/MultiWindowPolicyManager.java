/*
 * Created by Erjun.Ding 2015.03.15.
 * Policy for MultiWindow project.
 * Descrption: The default screen is 2 app shown but 1 app  when screen timeout. 
 */

package com.android.internal.policy.impl;


import android.util.Log;
import android.view.WindowManagerPolicy;
import android.view.IWindowManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ActivityManager.StackBoxInfo;
import android.app.ActivityManager.StackInfo;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

public class MultiWindowPolicyManager {

    static final String TAG = "MultiWindowPolicyManager";
    static final boolean DEBUG = true;
 
    private WindowManagerPolicy mPolicy;
    //private IWindowManager mWM;
    private IActivityManager mAm;
    Context mContext;

    static final int MODE_DEFAULT = 0;
    static final int MODE_BUSINESS = 1;
    static final int MODE_ADVERTISEMENT = 2;
    public int mState = MODE_DEFAULT;


    static final int STACKBOX_STATE_DEFAULT = 0;
    static final int STACKBOX_STATE_HOME = 1;
    static final int STACKBOX_STATE_APP = 2;
    static final int STACKBOX_STATE_MULTIWINDOW = 3;
//    public int mStackBoxState = STACKBOX_STATE_DEFAULT;

    Intent mBusinessIntent;
    Intent mAdvertisementIntent;


    static final int HOMESTACK = 0;
    StackBoxInfo mMultiWindowStackBoxInfo;

    public MultiWindowPolicyManager(WindowManagerPolicy policy,
            //IWindowManager windowManager) {
            Context context) {
    	if (DEBUG) Log.e(TAG,"MultiWindowPolicyManager create");
    		
    	
        mPolicy = policy;
        //mWM = windowManager;
        mAm = ActivityManagerNative.getDefault();
        mContext = context;
        mBusinessIntent =  new Intent(Intent.ACTION_MAIN, null);
        mBusinessIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mBusinessIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mBusinessIntent.setComponent(new ComponentName("com.ebox","com.ebox.ui.TopActivity"));

//        mBusinessIntent.setComponent(new ComponentName("com.android.development","com.android.development.Development"));
                
        mAdvertisementIntent =  new Intent(Intent.ACTION_MAIN, null);
        mAdvertisementIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mAdvertisementIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mAdvertisementIntent.setComponent(new ComponentName("com.android.settings","com.android.settings.Settings"));
 

    }
    

    public void setState (int state){
    	if (DEBUG) Log.e(TAG," setState :" + state);
        if (mState == state)
            return;
        mState  = state;

        if (state == MODE_BUSINESS)
            enterBusinessMode();
        else if (state == MODE_ADVERTISEMENT)
            enterAdvertisementMode();
    } 
    
    private int checkStackBoxState(){
        boolean hasMultiWindow = false;
        int stackBoxState = STACKBOX_STATE_DEFAULT;
        try {
            List<StackBoxInfo> stackBoxes = mAm.getStackBoxes();
            if (DEBUG) Log.e(TAG," checkStackBoxState stackBoxes：" +stackBoxes );
            for (StackBoxInfo info : stackBoxes) {
                 if (info.stackId == HOMESTACK) {
                	 stackBoxState = STACKBOX_STATE_HOME;
                	  continue;
                 } else if (info.stackId == -1) {
                     mMultiWindowStackBoxInfo = info;
                     stackBoxState = STACKBOX_STATE_MULTIWINDOW;
                     hasMultiWindow = true; 
                     break;
                     
                 } else if (info.stackId > HOMESTACK) {
                     mMultiWindowStackBoxInfo = info; 
                     stackBoxState = STACKBOX_STATE_APP;
                     break;
                 }
            }
            
        } catch (RemoteException e) {
        	 if (DEBUG) Log.e(TAG," checkStackBoxState RemoteException" );
        }
        if (DEBUG) Log.e(TAG," checkStackBoxState return：" +stackBoxState );
        return stackBoxState;
    }

    private void enterBusinessMode() {
    	if (DEBUG) Log.e(TAG," enterBusinessMode " );
         int stackBoxState = checkStackBoxState(); 
         if (stackBoxState == STACKBOX_STATE_MULTIWINDOW)
             return;
         try {
         if (stackBoxState == STACKBOX_STATE_HOME) {
        	 if (DEBUG) Log.e(TAG," enterBusinessMode  mAdvertisementIntent" );
             //mAm.startActivity(null, null, mAdvertisementIntent, null, null, null, 0, 0, null, null , null);
             mContext.startActivity(mAdvertisementIntent);
             stackBoxState = checkStackBoxState(); 
         }
         if (stackBoxState == STACKBOX_STATE_APP) {
        	 mContext.startActivity(mBusinessIntent);
        	 checkStackBoxState();
                          
             int topTask = mMultiWindowStackBoxInfo.stack.taskIds[mMultiWindowStackBoxInfo.stack.taskIds.length -1 ];
        	 if (DEBUG) Log.e(TAG," enterBusinessMode  createStack" );
             int stackId = mAm.createStack(topTask, mMultiWindowStackBoxInfo.stackBoxId,4 , Float.valueOf("0.33"));
             //mAm.setFocusedStack(stackId);
             if (DEBUG) Log.e(TAG," enterBusinessMode  mBusinessIntent" );
             //mAm.startActivity(null, null, mBusinessIntent, null, null, null, 0, 0, null, null, null);
             
             

             //mAm.moveTaskToStack(toStackTopTask, stackId, true);
         } 
         } catch (RemoteException e) {
         }
    }

    private void enterAdvertisementMode() {
         int stackBoxState = checkStackBoxState(); 
         if (stackBoxState == STACKBOX_STATE_APP) {
             return;
         }

//        try {
         if (stackBoxState == STACKBOX_STATE_HOME) {
             //mAm.startActivity(null, null, mAdvertisementIntent, null, null, null, 0, 0, null, null, null);
             mContext.startActivity(mAdvertisementIntent);
             return;
         }
         if (stackBoxState == STACKBOX_STATE_MULTIWINDOW) {
        	 StackBoxInfo fromStack = mMultiWindowStackBoxInfo.children[0];
        	 StackBoxInfo toStack = mMultiWindowStackBoxInfo.children[1];
        	 if (DEBUG) Log.e(TAG," enterAdvertisementMode fromStack:" +fromStack );
        	 if (DEBUG) Log.e(TAG," enterAdvertisementMode toStack:" +toStack );
        	 if (DEBUG) Log.e(TAG," enterAdvertisementMode  STACKBOX_STATE_MULTIWINDOW" );
        	 
        	 int toStackTopTask = toStack.stack.taskIds[toStack.stack.taskIds.length -1 ];
        	 if (DEBUG) Log.e(TAG," enterAdvertisementMode toStackTopTask:" +toStackTopTask );
             moveStack(fromStack, mMultiWindowStackBoxInfo.children[1]);
             try {
             mAm.moveTaskToFront(toStackTopTask, 0, null);
         } catch (RemoteException e) {
         }

         }
//         } catch (RemoteException e) {
//         }


    }

    private void moveStack(StackBoxInfo fromStack, StackBoxInfo toStack){
        int[] fromTaskIds = fromStack.stack.taskIds;
        int targetStackId = toStack.stackId;
        if (DEBUG) Log.e(TAG," enterAdvertisementMode fromTaskIds:" +fromTaskIds );
        
        try {
        for (int i : fromTaskIds) {
        	if (DEBUG) Log.e(TAG," enterAdvertisementMode moveTaskToStack index:" +i );
             mAm.moveTaskToStack(i , targetStackId, false);
         }
         } catch (RemoteException e) {
         }

    }


}
