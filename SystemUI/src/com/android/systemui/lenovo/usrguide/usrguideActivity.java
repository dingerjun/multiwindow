package com.android.systemui.lenovo.usrguide;

import com.android.systemui.R;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.support.v4.view.ViewPager;
import android.support.v4.view.PagerAdapter;
import com.android.systemui.recent.RecentsActivity;
import com.android.internal.statusbar.IStatusBarService;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.systemui.statusbar.BaseStatusBar;

public class usrguideActivity extends Activity implements OnClickListener,ViewPager.OnPageChangeListener{
    private Button bt;
    private ViewPager mpager;
    private ArrayList<View> pageViews;
    //private ImageButton firstDot;
    //private ImageButton secondDot;
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    IStatusBarService mStatusBarService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.lenovo_recent_usrguide_activity);
        mpager = (ViewPager)findViewById(R.id.view_pager);
        LayoutInflater inflater = getLayoutInflater();
        pageViews = new ArrayList<View>();
        pageViews.add(inflater.inflate(R.layout.lenovo_recent_usrguide_first, null));
        pageViews.add(inflater.inflate(R.layout.lenovo_recent_usrguide_second, null));
        pageViews.add(inflater.inflate(R.layout.lenovo_recent_usrguide_third, null));
        bt = (Button) pageViews.get(2).findViewById(R.id.recent_start_bt);
        bt.setOnClickListener(this);
        //firstDot = (ImageButton)findViewById(R.id.first_dot);
        //secondDot = (ImageButton)findViewById(R.id.second_dot);
        //firstDot.setEnabled(true);
        //secondDot.setEnabled(false);
        mpager.setAdapter(new MyPagerAdapter());
        mpager.setOnPageChangeListener(this);
        getStatusBarService();
    }

    IStatusBarService getStatusBarService() {
        if (mStatusBarService == null) {
            mStatusBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService("statusbar"));
        }
        return mStatusBarService;
    }
    
    @Override
    public void onClick(View v) {
        //SharedPreferences sp = this.getSharedPreferences("first_start", 0);
        /*boolean isFirstStart = sp.getBoolean("is_first_start", true);
        if(isFirstStart){
            Intent intent = new Intent("com.android.systemui.recent.RecentsActivity");
            sendBroadcast(intent);
        }*/
        try {
            IStatusBarService statusbar = getStatusBarService();
            if (statusbar != null) {
                Settings.Global.putInt(this.getContentResolver(),
                        Settings.Global.SYSTEM_UI_USRGUIDE, 1);
                statusbar.toggleRecentApps();
            }
        } catch (RemoteException e) {
            //Slog.e(TAG, "RemoteException when showing recent apps", e);
            // re-acquire status bar service next time it is needed.
            mStatusBarService = null;
        }
        finish();
    }

    class MyPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return pageViews.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ((ViewPager)container).addView(pageViews.get(position));
            return pageViews.get(position);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "";
        }
    }

    @Override
    public void onPageScrollStateChanged(int arg0) {

    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {

    }

    @Override
    public void onPageSelected(int position) {
        /*if(position == 0){
            firstDot.setEnabled(true);
            secondDot.setEnabled(false);
        }*/
        if(position == 2){
            Settings.Global.putInt(this.getContentResolver(),
                    Settings.Global.SYSTEM_UI_USRGUIDE, 1);
        }
    }
}
