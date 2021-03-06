package com.android.systemui.splitbar.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.widget.RelativeLayout;

import com.android.systemui.splitbar.view.animation.InOutAnimation;

public class InOutRelativeLayout extends RelativeLayout {

    private Animation mAnimation;

    public InOutRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public InOutRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InOutRelativeLayout(Context context) {
        super(context);
    }

    @Override
    protected void onAnimationEnd() {
        super.onAnimationEnd();
        if (this.mAnimation instanceof InOutAnimation) {
            setVisibility(((InOutAnimation) mAnimation).mDirection != InOutAnimation.Direction.OUT ? View.VISIBLE
                    : View.GONE);
        }
    }

    @Override
    protected void onAnimationStart() {
        super.onAnimationStart();
        if (this.mAnimation instanceof InOutAnimation) {
            setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void startAnimation(Animation animation) {
        super.startAnimation(animation);
        this.mAnimation = animation;
        getRootView().postInvalidate();
    }
}