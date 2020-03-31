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

package com.android.systemui.power;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.IUsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import com.android.systemui.usb.UsbDisconnectedReceiver;

import com.android.systemui.R;

import android.provider.Settings;
import android.widget.Toast ;
import android.content.Intent;

import com.android.systemui.BatteryInfoService;


public class EnergyBayActivity extends AlertActivity
        implements DialogInterface.OnClickListener,
        CheckBox.OnCheckedChangeListener{

    private static final String TAG = "EnergyBayActivity";
    private static final String ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING 
                   = "action.usb.host.flag.low.battery.warning" ;

    private CheckBox mEb_cb;
    //private TextView mClearDefaultHint;
    //private UsbDevice mDevice;
    //private UsbAccessory mAccessory;
    //private ResolveInfo mResolveInfo;
    //private boolean mPermissionGranted;
    private AlertDialog mEnergyBayAlertDialog = null;
    private UsbDisconnectedReceiver mDisconnectedReceiver;
    private UsbDevice mDevice ;
    private String mApMessage = "" ;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.d(TAG, "EnergyBayActivity.onCreate");

        /** zero ,analysis the intent **/
        Intent intent = getIntent();
        String action = null ;
        action = intent.getAction();

        Log.d(TAG, "action = " + action);

        if(action == null || action.length() <= 0){
            action = intent.getStringExtra("action");
        }
        action = (action != null && action.length() > 0) ? action:"action" ;

        Log.d(TAG, "action = " + action);

        /** first for low battery status **/
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
            if(getFlagLowBateryUsbhost(this)){
                saveBatteryUsbhostStatus(this,action);
                action = ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING ;
            }
            refreshActivitySelf(this);
        }
        /** first 1.1, for usb device not boot completed **/
        if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            int value = Settings.System.getInt(this.getContentResolver(),KeyBatteryUsbhostStatus,-1);
            if(value < 1){
                // do nothing
                finish();
                return ;
            }
        }
       
        /** second adjust for really action **/
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
    	   mApMessage = getString(R.string.dlg_energyBay_msg_supplying);
    	   mDisconnectedReceiver = new UsbDisconnectedReceiver(this, mDevice);
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
    	   mApMessage = getString(R.string.dlg_energyBay_msg_nosupply);
    	   //mDisconnectedReceiver = new UsbDisconnectedReceiver(this);
        }else if(ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING.equals(action)){
    	   mApMessage = getString(R.string.dlg_energyBay_msg_lowpower);
    	   mDisconnectedReceiver = new UsbDisconnectedReceiver(this);
        }
       
        /** third saveBatteryUsbhostStatus **/
        saveBatteryUsbhostStatus(this,action);
       
        /** fourth show dialog,ui **/
        //mDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        //mAccessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        //mResolveInfo = (ResolveInfo)intent.getParcelableExtra("rinfo");
        //
        //PackageManager packageManager = getPackageManager();
        //String appName = mResolveInfo.loadLabel(packageManager).toString();

        View view = getLayoutInflater().inflate(R.layout.energy_bay_dialog,null);
        TextView tv_dec = (TextView)view.findViewById(R.id.tv_declaration);
        tv_dec.setText(mApMessage);
        mEnergyBayAlertDialog = new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
        .setView(view)
        .setTitle(R.string.dlg_batterydistinguish_title)
        .setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveCheckedInXML();
                finish();
            }
        }).create();
        //mEnergyBayAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_DRAG);
        mEnergyBayAlertDialog.getWindow().setCloseOnTouchOutside(false);
        mEnergyBayAlertDialog.show();
        mEb_cb = (CheckBox)view.findViewById(R.id.cb_declaration);
        mEb_cb.setOnCheckedChangeListener(this);
        /**show checkbox or not **/
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
            if(getCheckedFromXML()){
                Toast.makeText(this.getApplicationContext(),
                this.getResources().getString(R.string.energyBay_open_usb_otg),
                Toast.LENGTH_SHORT).show();
                finish();
            }
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            Toast.makeText(this.getApplicationContext(),mApMessage,Toast.LENGTH_SHORT).show();
            finish();
        }else if(ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING.equals(action)){
            mEb_cb.setVisibility(View.GONE);
        }
        setupAlert();
    }
    
    private static final String refreshAction = "action.refresh.usb.host.status.changed" ;
    private final void refreshActivitySelf(Context mContext){
        Intent sendIntent = new Intent(refreshAction);
        mContext.sendBroadcast(sendIntent);
    }
    
    private static final String KeyBatteryUsbhostStatus = "bBatteryUsbhostStatusKey" ;
    private final void saveBatteryUsbhostStatus(Context mContext , String action){
    	
    	int bUsbhostOn = -1 ;
    	if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
            bUsbhostOn = 1 ;
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            bUsbhostOn = 0 ;
        }else{
        	return ;
        }
    	Log.d(TAG,"setBatteryUsbhostStatus ,bBatteryUsbhostStatus : " + bUsbhostOn);
    	int value = Settings.System.getInt(mContext.getContentResolver(),KeyBatteryUsbhostStatus,-1);
    	if(value != bUsbhostOn){
            // Toast.makeText(mContext,"saveBatteryUsbhostStatus ,UsbhostStatus:" + bUsbhostOn ,Toast.LENGTH_SHORT).show();
            Settings.System.putInt(mContext.getContentResolver(),KeyBatteryUsbhostStatus,bUsbhostOn);
            // sendIntent for action.usb.otg.status.changed
            Intent sendIntent = new Intent(BatteryInfoService.ACTION_USB_OTG_STATUS_CHANGED);
            sendIntent.putExtra("status", bUsbhostOn);
            mContext.sendBroadcast(sendIntent);
    	}
    }
    private static final String KeyFlagLowBateryUsbhost = "bFlagLowBateryUsbhost" ;
    private final boolean getFlagLowBateryUsbhost(Context mContext){
    	int value = Settings.System.getInt(mContext.getContentResolver(),KeyFlagLowBateryUsbhost,-1);
    	return value == 1 ;
    }

    @Override
    protected void onDestroy() {
        if (mDisconnectedReceiver != null) {
            unregisterReceiver(mDisconnectedReceiver);
        }
        super.onDestroy();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            this.cancel();
        }
        saveCheckedInXML();
        finish();
    }
    
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        //if (mClearDefaultHint == null) return;

        /** not show the desc **/
        /*if(isChecked) {
            mClearDefaultHint.setVisibility(View.VISIBLE);
        } else {
            mClearDefaultHint.setVisibility(View.GONE);
        }*/
    }
    
    /** for save and get configuration ,isChecked **/
    private void saveCheckedInXML(){
        try{
            boolean ischecked = false  ;
            if(mEb_cb != null)
                ischecked = mEb_cb.isChecked();

            SharedPreferences shared = this.getSharedPreferences("configuration",Context.MODE_WORLD_READABLE);
            SharedPreferences.Editor editor = shared.edit();
            editor.putBoolean("checked",ischecked);
            editor.commit();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private boolean getCheckedFromXML(){
        try{
            SharedPreferences shared = this.getSharedPreferences("configuration",Context.MODE_WORLD_READABLE);
            return shared.getBoolean("checked",false);
        }catch(Exception e){
            e.printStackTrace();
            return false ;
        }
    }
}
