package com.android.systemui.splitbar.view.animation;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;

import com.android.systemui.splitbar.view.InOutImageButton;
import com.android.systemui.R;

public class MenuButtonAnimation extends InOutAnimation {

    public static final int DURATION = 500;
    private static final int xOffset = -50;
    private static final int yOffset = 50;

    public MenuButtonAnimation(Direction direction, long l, View view) {
        super(direction, l, new View[] { view });
    }

    public static void startAnimations(ViewGroup viewgroup,
            InOutAnimation.Direction direction) {
        switch (direction) {
        case IN:
            startAnimationsIn(viewgroup);
            break;
        case OUT:
            startAnimationsOut(viewgroup);
            break;
        }
    }

    private static void startAnimationsIn(ViewGroup viewgroup) {
        for (int i = 0; i < viewgroup.getChildCount(); i++) {
            if (viewgroup.getChildAt(i) instanceof InOutImageButton) {
                InOutImageButton inoutimagebutton = (InOutImageButton) viewgroup
                        .getChildAt(i);
                MenuButtonAnimation animation = new MenuButtonAnimation(
                        InOutAnimation.Direction.IN, DURATION, inoutimagebutton);
                animation.setStartOffset((i * 100)
                        / (-1 + viewgroup.getChildCount()));
                animation.setInterpolator(new OvershootInterpolator(2F));
                inoutimagebutton.startAnimation(animation);
            }
        }
    }

    private static void startAnimationsOut(ViewGroup viewgroup) {
        for (int i = 0; i < viewgroup.getChildCount(); i++) {
            if (viewgroup.getChildAt(i) instanceof InOutImageButton) {
                InOutImageButton inoutimagebutton = (InOutImageButton) viewgroup
                        .getChildAt(i);
                MenuButtonAnimation animation = new MenuButtonAnimation(
                        InOutAnimation.Direction.OUT, DURATION,
                        inoutimagebutton);
                animation.setStartOffset((100 * ((-1 + viewgroup
                        .getChildCount()) - i))
                        / (-1 + viewgroup.getChildCount()));
                animation.setInterpolator(new AnticipateInterpolator(2F));
                inoutimagebutton.startAnimation(animation);
            }
        }
    }

    @Override
    protected void addInAnimation(View[] aview) {
        switch (aview[0].getId()) {
        case R.id.menu_add_first:
        case R.id.button_view_first:
            addAnimation(new TranslateAnimation(-xOffset, 0F, 0F, 0F));
            break;
        case R.id.button_view_second:
            addAnimation(new TranslateAnimation(-xOffset, 0F, yOffset, 0F));
            break;
        case R.id.menu_add_second:
        case R.id.button_view_third:
            addAnimation(new TranslateAnimation(0F, 0F, yOffset, 0F));
            break;
        case R.id.button_view_forth:
            addAnimation(new TranslateAnimation(xOffset, 0F, yOffset, 0F));
            break;
        case R.id.menu_max_first:
        case R.id.button_view_Fifth:
            addAnimation(new TranslateAnimation(xOffset, 0F, 0F, 0F));
            break;
        case R.id.button_view_sixth:
            addAnimation(new TranslateAnimation(xOffset, 0F, -yOffset, 0F));
            break;
        case R.id.menu_max_second:
        case R.id.button_view_seventh:
            addAnimation(new TranslateAnimation(0F, 0F, -yOffset, 0F));
            break;
        case R.id.button_view_eighth:
            addAnimation(new TranslateAnimation(-xOffset, 0F, -yOffset, 0F));
            break;
        }
    }

    @Override
    protected void addOutAnimation(View[] aview) {
        addAnimation(new TranslateAnimation(0F, 0F, 0F, 0F));
//        switch(aview[0].getId()){
//        case R.id.button_view_first:
//            addAnimation(new TranslateAnimation(0F, -xOffset, 0F, 0F));
//            break;
//        case R.id.button_view_second:
//            addAnimation(new TranslateAnimation(0F, -xOffset, 0F, yOffset));
//            break;
//        case R.id.button_view_third:
//            addAnimation(new TranslateAnimation(0F, 0F, 0F, yOffset));
//            break;
//        case R.id.button_view_forth:
//            addAnimation(new TranslateAnimation(0F, xOffset, 0F, yOffset));
//            break;
//        case R.id.button_view_Fifth:
//            addAnimation(new TranslateAnimation(0F, xOffset, 0F, 0F));
//            break;
//        case R.id.button_view_sixth:
//            addAnimation(new TranslateAnimation(0F, xOffset, 0F, -yOffset));
//            break;
//        case R.id.button_view_seventh:
//            addAnimation(new TranslateAnimation(0F, 0F, 0F, -yOffset));
//            break;
//        case R.id.button_view_eighth:
//            addAnimation(new TranslateAnimation(0F, -xOffset, 0F, -yOffset));
//            break;
//        }
    }
}
