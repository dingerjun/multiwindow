/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.wm;

import android.graphics.Rect;
import android.util.Slog;
import android.view.Surface;

import static com.android.server.am.ActivityStackSupervisor.HOME_STACK_ID;
import static com.android.server.wm.WindowManagerService.DEBUG_STACK;
import static com.android.server.wm.WindowManagerService.TAG;

import java.io.PrintWriter;

public class StackBox {
    /** Used with {@link WindowManagerService#createStack}. Dependent on Configuration LTR/RTL. */
    public static final int TASK_STACK_GOES_BEFORE = 0;
    /** Used with {@link WindowManagerService#createStack}. Dependent on Configuration LTR/RTL. */
    public static final int TASK_STACK_GOES_AFTER = 1;
    /** Used with {@link WindowManagerService#createStack}. Horizontal to left of. */
    public static final int TASK_STACK_TO_LEFT_OF = 2;
    /** Used with {@link WindowManagerService#createStack}. Horizontal to right of. */
    public static final int TASK_STACK_TO_RIGHT_OF = 3;
    /** Used with {@link WindowManagerService#createStack}. Vertical: lower t/b Rect values. */
    public static final int TASK_STACK_GOES_ABOVE = 4;
    /** Used with {@link WindowManagerService#createStack}. Vertical: higher t/b Rect values. */
    public static final int TASK_STACK_GOES_BELOW = 5;
    /** Used with {@link WindowManagerService#createStack}. Put on a higher layer on display. */
    public static final int TASK_STACK_GOES_OVER = 6;
    /** Used with {@link WindowManagerService#createStack}. Put on a lower layer on display. */
    public static final int TASK_STACK_GOES_UNDER = 7;

    /*Lenovo sw, xieqiong2 2014.1.23 add BLADEFHD-283*/
    public static final int SPLIT_SPACE = 8;

    static int sCurrentBoxId = 0;

    /** Unique id for this box */
    final int mStackBoxId;

    /** The service */
    final WindowManagerService mService;

    /** The display this box sits in. */
    final DisplayContent mDisplayContent;

    /** Non-null indicates this is mFirst or mSecond of a parent StackBox. Null indicates this
     * is this entire size of mDisplayContent. */
    StackBox mParent;

    /** First child, this is null exactly when mStack is non-null. */
    StackBox mFirst;

    /** Second child, this is null exactly when mStack is non-null. */
    StackBox mSecond;

    /** Stack of Tasks, this is null exactly when mFirst and mSecond are non-null. */
    TaskStack mStack;

    /** Content limits relative to the DisplayContent this sits in. */
    Rect mBounds = new Rect();

    /** Relative orientation of mFirst and mSecond. */
    boolean mVertical;

    /** Fraction of mBounds to devote to mFirst, remainder goes to mSecond */
    float mWeight;

    /** Dirty flag. Something inside this or some descendant of this has changed. */
    boolean layoutNeeded;

    /** True if this StackBox sits below the Status Bar. */
    boolean mUnderStatusBar;

    /** Used to keep from reallocating a temporary Rect for propagating bounds to child boxes */
    Rect mTmpRect = new Rect();

    StackBox(WindowManagerService service, DisplayContent displayContent, StackBox parent) {
        synchronized (StackBox.class) {
            mStackBoxId = sCurrentBoxId++;
        }

        mService = service;
        mDisplayContent = displayContent;
        mParent = parent;
    }

    /** Propagate #layoutNeeded bottom up. */
    void makeDirty() {
        layoutNeeded = true;
        if (mParent != null) {
            mParent.makeDirty();
        }
    }

    /**
     * Determine if a particular StackBox is this one or a descendant of this one.
     * @param stackBoxId The StackBox being searched for.
     * @return true if the specified StackBox matches this or one of its descendants.
     */
    boolean contains(int stackBoxId) {
        return mStackBoxId == stackBoxId ||
                (mStack == null &&  ((mFirst!=null && mFirst.contains(stackBoxId)) || (mSecond!=null && mSecond.contains(stackBoxId))));
    }

    /**
     * Return the stackId of the stack that intersects the passed point.
     * @param x coordinate of point.
     * @param y coordinate of point.
     * @return -1 if point is outside of mBounds, otherwise the stackId of the containing stack.
     */
    int stackIdFromPoint(int x, int y) {
        if (!mBounds.contains(x, y)) {
            return -1;
        }
        if (mStack != null) {
            return mStack.mStackId;
        }
        int stackId =0;
        if (mFirst!=null){
        	stackId = mFirst.stackIdFromPoint(x, y);
	        if (stackId >= 0) {
	            return stackId;
	        }
        }
        if (mSecond!=null){
        	stackId = mSecond.stackIdFromPoint(x, y);
	        if (stackId >= 0) {
	            return stackId;
	        }
        }        
        return -1;
    }

    /** Determine if this StackBox is the first child or second child.
     * @return true if this is the first child.
     */
    boolean isFirstChild() {
        return mParent != null && mParent.mFirst == this;
    }

    /** Returns the bounds of the specified TaskStack if it is contained in this StackBox.
     * @param stackId the TaskStack to find the bounds of.
     * @return a new Rect with the bounds of stackId if it is within this StackBox, null otherwise.
     */
    Rect getStackBounds(int stackId) {
        if (mStack != null) {
            return mStack.mStackId == stackId ? new Rect(mBounds) : null;
        }
        Rect bounds = null;
        if (mFirst!=null){
	        bounds = mFirst.getStackBounds(stackId);
	        if (bounds != null) {
	            return bounds;
	        }
        }
        
        if (mSecond!=null){
        	bounds = mSecond.getStackBounds(stackId);
	        if (bounds != null) {
	            return bounds;
	        }        	
        }
        
        return new Rect(0,0,0,0);
    }

    /**
     * Create a new TaskStack relative to a specified one by splitting the StackBox containing
     * the specified TaskStack into two children. The size and position each of the new StackBoxes
     * is determined by the passed parameters.
     * @param stackId The id of the new TaskStack to create.
     * @param relativeStackBoxId The id of the StackBox to place the new TaskStack next to.
     * @param position One of the static TASK_STACK_GOES_xxx positions defined in this class.
     * @param weight The percentage size of the parent StackBox to devote to the new TaskStack.
     * @return The new TaskStack.
     */
    TaskStack split(int stackId, int relativeStackBoxId, int position, float weight) {
        if (mStackBoxId != relativeStackBoxId) {
            // This is not the targeted StackBox.
            if (mStack != null) {
                return null;
            }
            // Propagate the split to see if the targeted StackBox is in either sub box.
            TaskStack stack=null;
            if (mFirst!=null){
	            stack = mFirst.split(stackId, relativeStackBoxId, position, weight);
	            if (stack != null) {
	                return stack;
	            }
            }
            if (mSecond!=null){
	            stack = mSecond.split(stackId, relativeStackBoxId, position, weight);
	            if (stack != null) {
	                return stack;
	            }
            }            
            return null;
        }

        // Found it!
        TaskStack stack = new TaskStack(mService, stackId, mDisplayContent);
        TaskStack firstStack;
        TaskStack secondStack;
        if (position == TASK_STACK_GOES_BEFORE) {
            // TODO: Test Configuration here for LTR/RTL.
            position = TASK_STACK_TO_LEFT_OF;
        } else if (position == TASK_STACK_GOES_AFTER) {
            // TODO: Test Configuration here for LTR/RTL.
            position = TASK_STACK_TO_RIGHT_OF;
        }
        switch (position) {
            default:
            case TASK_STACK_TO_LEFT_OF:
            case TASK_STACK_TO_RIGHT_OF:
                mVertical = false;
                if (position == TASK_STACK_TO_LEFT_OF) {
                    mWeight = weight;
                    firstStack = stack;
                    secondStack = mStack;
                } else {
                    mWeight = 1.0f - weight;
                    firstStack = mStack;
                    secondStack = stack;
                }
                break;
            case TASK_STACK_GOES_ABOVE:
            case TASK_STACK_GOES_BELOW:
                mVertical = true;
                if (position == TASK_STACK_GOES_ABOVE) {
                    mWeight = weight;
                    firstStack = stack;
                    secondStack = mStack;
                } else {
                    mWeight = 1.0f - weight;
                    firstStack = mStack;
                    secondStack = stack;
                }
                break;
        }

        mFirst = new StackBox(mService, mDisplayContent, this);
        if (mFirst!=null){
            firstStack.mStackBox = mFirst;
            mFirst.mStack = firstStack;
        }

        mSecond = new StackBox(mService, mDisplayContent, this);
        if (mSecond!=null){
            secondStack.mStackBox = mSecond;
            mSecond.mStack = secondStack;
        }
        mStack = null;
        return stack;
    }

    /** Return the stackId of the first mFirst StackBox with a non-null mStack */
    int getStackId() {
        if (mStack != null) {
            return mStack.mStackId;
        }
        if (mFirst!=null){
        	return mFirst.getStackId();
        }
        return 0;
    }

    /** Remove this box and propagate its sibling's content up to their parent.
     * @return The first stackId of the resulting StackBox. */
    int remove() {
        mDisplayContent.layoutNeeded = true;

        if (mParent == null) {
            // This is the top-plane stack.
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: removing top plane.");
            mDisplayContent.removeStackBox(this);
            return HOME_STACK_ID;
        }

        StackBox sibling = isFirstChild() ? mParent.mSecond : mParent.mFirst;
        StackBox grandparent = mParent.mParent;
        sibling.mParent = grandparent;
        if (grandparent == null) {
            // mParent is a top-plane stack. Now sibling will be.
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: grandparent null");
            mDisplayContent.removeStackBox(mParent);
            sibling.mVertical=!sibling.mVertical;
//dingej1 2014,2,19 BLADEFHD-2572 do not add Top when HomeStack is top.
            mDisplayContent.addStackBox(sibling, !mDisplayContent.homeOnTop());
//dingej1 end.
        } else {
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: grandparent getting sibling");
            if (mParent.isFirstChild()) {
                grandparent.mFirst = sibling;
            } else {
                grandparent.mSecond = sibling;
            }
        }
        return sibling.getStackId();
    }

    boolean resize(int stackBoxId, float weight) {
        if (mStackBoxId != stackBoxId) {
            return mStack == null &&
                    (mFirst!=null && mFirst.resize(stackBoxId, weight)) || (mSecond!=null && mSecond.resize(stackBoxId, weight));
        }
        // Don't change weight on topmost stack.
        if (mParent != null) {
            mParent.mWeight = isFirstChild() ? weight : 1.0f - weight;
        }
        return true;
    }

    boolean isPortal(){
    	int rot = mService.getRotation();
        if (mDisplayContent.mInitialDisplayWidth < mDisplayContent.mInitialDisplayHeight) {
            // On devices with a natural orientation of portrait
            switch (rot) {
                default:
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    return true;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                	return false;
            }
        }else {
	        // On devices with a natural orientation of landscape
	        switch (rot) {
		        default:
		        case Surface.ROTATION_0:
		        case Surface.ROTATION_180:
		            return false;
		        case Surface.ROTATION_90:
		        case Surface.ROTATION_270:
		        	return true;
		    }
        }
    }
    
    /** If this is a terminal StackBox (contains a TaskStack) set the bounds.
     * @param bounds The rectangle to set the bounds to.
     * @param underStatusBar True if the StackBox is directly below the Status Bar.
     * @return True if the bounds changed, false otherwise. */
    boolean setStackBoxSizes(Rect bounds, boolean underStatusBar) {
        boolean change = false;
        if (mUnderStatusBar != underStatusBar) {
            change = true;
            mUnderStatusBar = underStatusBar;
        }
        if (mStack != null) {
            change |= !mBounds.equals(bounds);
            //if (change) { //there is a bug in aosp code which causes 2nd taskstack issue
            if (true) {
                mBounds.set(bounds);
                mStack.setBounds(bounds, underStatusBar);
            }
        } else {
            mBounds.set(bounds);
            mTmpRect.set(bounds);
            if (!(isPortal() ^ mVertical)) {
                final int height = bounds.height();
                int firstHeight = (int)(height * mWeight);
                mTmpRect.bottom = bounds.top + firstHeight - SPLIT_SPACE/2;
                if (mFirst!=null)
                change |= mFirst.setStackBoxSizes(mTmpRect, underStatusBar);
                /*Lenovo sw, xieqiong2 2014.1.16 modify BLADEFHD-283*/
                mTmpRect.top = mTmpRect.bottom + SPLIT_SPACE;
                mTmpRect.bottom = bounds.top + height;
                if (mSecond!=null)
                change |= mSecond.setStackBoxSizes(mTmpRect, false);
            } else {
                final int width = bounds.width();
                int firstWidth = (int)(width * mWeight);
                mTmpRect.right = bounds.left + firstWidth - SPLIT_SPACE/2+1;
                if (mFirst!=null)
                change |= mFirst.setStackBoxSizes(mTmpRect, underStatusBar);
                /*Lenovo sw, xieqiong2 2014.1.16 modify BLADEFHD-283*/
                mTmpRect.left = mTmpRect.right + SPLIT_SPACE;
                mTmpRect.right = bounds.left + width;
                if (mSecond!=null)
                change |= mSecond.setStackBoxSizes(mTmpRect, underStatusBar);
            }
        }
        return change;
    }

    void resetAnimationBackgroundAnimator() {
        if (mStack != null) {
            mStack.resetAnimationBackgroundAnimator();
            return;
        }
        if (mFirst!=null)
        mFirst.resetAnimationBackgroundAnimator();
        if (mSecond!=null)
        mSecond.resetAnimationBackgroundAnimator();
    }

    boolean animateDimLayers() {
        if (mStack != null) {
            return mStack.animateDimLayers();
        }
        boolean result=false;
        if (mFirst!=null){
        	result = mFirst.animateDimLayers();
        }
        if (mSecond!=null){
        	result |= mSecond.animateDimLayers();
        }
        return result;
    }

    void resetDimming() {
        if (mStack != null) {
            mStack.resetDimmingTag();
            return;
        }
        if (mFirst!=null)
        mFirst.resetDimming();
        if (mSecond!=null)
        mSecond.resetDimming();
    }

    boolean isDimming() {
        if (mStack != null) {
            return mStack.isDimming();
        }
        boolean result=false;
        if (mFirst!=null){
        	result = mFirst.isDimming();
        }
        if (mSecond!=null){
        	result |= mSecond.isDimming();
        }
        return result;
    }

    void stopDimmingIfNeeded() {
        if (mStack != null) {
            mStack.stopDimmingIfNeeded();
            return;
        }
        if (mFirst!=null)
        mFirst.stopDimmingIfNeeded();
        if (mSecond!=null)
        mSecond.stopDimmingIfNeeded();
    }

    void switchUserStacks(int userId) {
        if (mStack != null) {
            mStack.switchUser(userId);
            return;
        }
        if (mFirst!=null)
        mFirst.switchUserStacks(userId);
        if (mSecond!=null)
        mSecond.switchUserStacks(userId);
    }

    void close() {
        if (mStack != null) {
            mStack.mDimLayer.mDimSurface.destroy();
            mStack.mAnimationBackgroundSurface.mDimSurface.destroy();
            return;
        }
        if (mFirst!=null)
        mFirst.close();
        if (mSecond!=null)
        mSecond.close();
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mParent="); pw.println(mParent);
        pw.print(prefix); pw.print("mBounds="); pw.print(mBounds.toShortString());
            pw.print(" mVertical="); pw.print(mVertical);
            pw.print(" layoutNeeded="); pw.println(layoutNeeded);
        if (mFirst != null && mSecond!=null) {
            pw.print(prefix); pw.print("mFirst="); pw.println(System.identityHashCode(mFirst));
            mFirst.dump(prefix + "  ", pw);
            pw.print(prefix); pw.print("mSecond="); pw.println(System.identityHashCode(mSecond));
            mSecond.dump(prefix + "  ", pw);
        } else if (mStack != null){
            pw.print(prefix); pw.print("mStack="); pw.println(mStack);
            mStack.dump(prefix + "  ", pw);
        }
    }

    @Override
    public String toString() {
        if (mStack != null) {
            return "Box{" + hashCode() + " stack=" + mStack.mStackId + "}";
        }else if (mFirst != null && mSecond!=null){
        return "Box{" + hashCode() + " parent=" + System.identityHashCode(mParent)
                + " first=" + System.identityHashCode(mFirst)
                + " second=" + System.identityHashCode(mSecond) + "}";
        }else {
        	return "";
        }
    }
}
