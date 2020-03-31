package com.android.systemui;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import android.widget.Toast ;

import com.android.systemui.R ;

import android.database.ContentObserver;
import android.provider.Settings;

import android.text.format.Time;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.util.HashMap ;

import android.provider.Settings;

import android.hardware.usb.UsbDevice ;
import android.hardware.usb.UsbManager ;


public class BatteryInfoService extends Service {

    public static final int notificationId = 101110011;

    private static final String TAG = "BatteryInfoService" ;
    private Context mContext = null ;

    /** usb_otg_status , status == 1 on , status == 0 off , status == -1 unknown ,**/
    private static int usb_otg_status = -1 ;
    private static int batterylevel = 100 ;

    private String content = "" ;

    public static final String ACTION_USB_OTG_STATUS_CHANGED = "action.usb.otg.status.changed" ;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();

        Log.d(TAG,"onCreate");
        mContext = getApplication().getApplicationContext();
        resetDBdataUsbhostStatus();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ACTION_USB_OTG_STATUS_CHANGED);
        registerReceiver(mBatteryInfoReceiver, filter);

        mContext.getContentResolver().registerContentObserver(
        Settings.System.getUriFor(KeyFlagLowBateryUsbhost),
        false, usbhostValueObserver);

        showNotificationWhenBootCompleted();

    }

    private static final String KeyBatteryUsbhostStatus = "bBatteryUsbhostStatusKey" ;
    private void showNotificationWhenBootCompleted(){
        Log.d(TAG,"showNotificationWhenBootCompleted");

        UsbManager usbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String,UsbDevice> deviceList = usbManager.getDeviceList();
        if(deviceList.size() < 1 ){
            return;
        }else{
            usb_otg_status = 1 ;
            content = getNotificationTitle();
            showNotification(mContext,R.drawable.usb_otg_statusbar,content);
        }
    }

    private BatteryInfoReceiver mBatteryInfoReceiver = new BatteryInfoReceiver();
    private FlagLowBateryUsbhostValueObserver usbhostValueObserver = new FlagLowBateryUsbhostValueObserver();

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.d(TAG,"onDestroy");
        super.onDestroy();
        unregisterReceiver(mBatteryInfoReceiver);
        mContext.getContentResolver().unregisterContentObserver(usbhostValueObserver);
        resetDBdataUsbhostStatus();
        cancelNotification(mContext);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub

        //if(intent.getAction().equals(boot_start)){
        //Log.d(TAG,"onStartCommand ,intent.action:" + intent.getAction());
        //}
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // TODO Auto-generated method stub
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }


    private static final String KeyFlagLowBateryUsbhost = "bFlagLowBateryUsbhost" ;
    private class FlagLowBateryUsbhostValueObserver extends ContentObserver {
        public FlagLowBateryUsbhostValueObserver() {
            super(null);
        }
        @Override
        public void onChange(boolean selfChange) {

            Log.d(TAG,"FlagLowBateryUsbhostValueObserver.onChange");
            int value = Settings.System.getInt(mContext.getContentResolver(),KeyFlagLowBateryUsbhost,-1);
            sendFlagLowBateryUsbhost(value);
        }
    }

    private final void sendFlagLowBateryUsbhost(int value){
        int status = Settings.System.getInt(mContext.getContentResolver(),KeyBatteryUsbhostStatus,-1);
        if(status != 1)return ;
        Intent sendIntent = new Intent(BootReceiver.ACTION_USB_HOST_FLAG_LOW_BATTERY);
        sendIntent.putExtra("status", value);
        mContext.sendBroadcast(sendIntent);
    }

    private final void resetDBdataUsbhostStatus(){
        Settings.System.putInt(mContext.getContentResolver(),KeyBatteryUsbhostStatus,-1);
        //Settings.System.putInt(mContext.getContentResolver(),KeyFlagLowBateryUsbhost, -1);
    }


    private void showNotification(Context context, int drawable,String content) {

        Log.d(TAG,"showNotification(Context context, int drawable,String content)");
        //Toast.makeText(mContext,"BatteryInfoService.showNotification",Toast.LENGTH_SHORT).show();

        NotificationManager notificationManager = (NotificationManager)
        context.getSystemService(Context.NOTIFICATION_SERVICE);

        RemoteViews contentView = new RemoteViews(context.getPackageName(),
        R.layout.usb_otg_notification_item_view);
        Intent backIntent = new Intent();
        backIntent.setAction("android.intent.action.MAIN");
        backIntent.setClassName("com.lenovo.powersaving", "com.lenovo.powersaving.MainScreen2");

        contentView.setTextViewText(R.id.txt_ms, content);
        contentView.setTextViewText(R.id.txt_bfb, getString(R.string.remaining_battery_capacity, batterylevel));
        contentView.setProgressBar(R.id.prog_tzl, 100, batterylevel, false);
        PendingIntent contentItent = PendingIntent.getActivity(context, 0,backIntent, 0);

        /*PendingIntent contentItent = PendingIntent.getActivity(context, 0,
        backIntent, 0);*/
        Notification.Builder builder = new Notification.Builder(context);
        builder.setContent(contentView);
        builder.setSmallIcon(drawable);
        //builder.setContentIntent(contentItent);
        Notification n = builder.getNotification();
        n.flags = Notification.FLAG_NO_CLEAR;
        notificationManager.notify(notificationId, n);
    }
    //private void showNotification(Context context, int drawable) {
    //Toast.makeText(context,"showNotification" + batterylevel ,Toast.LENGTH_SHORT).show();
    //}

    public void cancelNotification(Context context) {
        Log.d(TAG,"cancelNotification(Context context)");
        NotificationManager notificationManager = (NotificationManager)
        context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    public String getNotificationTitle(){
        /*SimpleDateFormat sdf = new SimpleDateFormat("HH:mm",Locale.SIMPLIFIED_CHINESE);
        //sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
        long currentReal = Long.valueOf(System.currentTimeMillis()); // System.currentTimeMillis() on the phone setting
        //long currentCalc = Long.valueOf(SystemClock.elapsedRealtime()); // SystemClock.elapsedRealtime() standby time when boot
        String currentDateStr = sdf.format(new Date(currentReal)) ;*/
        // only in 24-hour time system
        //Time localTime = new Time();
        //localTime.setToNow();
        //String currentDateStr = localTime.format("%H:%M");

        return mContext.getResources().getString(R.string.energyBay_notificaton_content);
    }

    public class BatteryInfoReceiver extends BroadcastReceiver {

        private static final String TAG = "BatteryInfoReceiver" ;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive action:" + action);

            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {

                int level = intent.getIntExtra("level", 0);
                int scale = intent.getIntExtra("scale", 100);
                batterylevel  = level*100/scale ;
                Log.d("WYQ", "usb_otg_status = " + usb_otg_status);
                // if usb_otg_on = true ,show notificationS
                if(usb_otg_status ==1){
                    showNotification(context,R.drawable.usb_otg_statusbar,content);
                }
            }else if (ACTION_USB_OTG_STATUS_CHANGED.equals(action)) {

                usb_otg_status = intent.getIntExtra("status",-1);
                // Toast.makeText(mContext,"BatteryInfoService.showNotification.usb_otg_status" + usb_otg_status,Toast.LENGTH_SHORT).show();

                // if usb_otg_on = true ,show notification
                // if usb_otg_on = off  ,remove notification
                if(usb_otg_status ==1){
                    content = getNotificationTitle() ;
                    showNotification(context,R.drawable.usb_otg_statusbar,content);
                }else if(usb_otg_status ==0){
                    cancelNotification(context);
                }
            }
            //end if/else part
        }//end onReceive
    } //end class BatteryInfoReceiver
}
