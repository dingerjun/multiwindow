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

package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import com.android.systemui.power.EnergyBayActivity;
import android.util.Log;
import java.util.HashMap ;
import android.hardware.usb.UsbDevice ;
import android.hardware.usb.UsbManager ;
import android.provider.Settings;
import android.widget.Toast ;

/**
 * Performs a number of miscellaneous, non-system-critical actions
 * after the system has finished booting.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SystemUIBootReceiver";
    private static final String ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING
                = "action.usb.host.flag.low.battery.warning" ;

    public static final String ACTION_USB_HOST_FLAG_LOW_BATTERY
                = "action.usb.host.flag.low.battery" ;
    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG,"BootReceiver.onReceive ,intent.action : " + action);
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)){
            try {
                    // Start the load average overlay, if activated
                    ContentResolver res = context.getContentResolver();
                    if (Settings.Global.getInt(res, Settings.Global.SHOW_PROCESSES, 0) != 0) {
                        Intent loadavg = new Intent(context, com.android.systemui.LoadAverageService.class);
                        context.startService(loadavg);
                    }
                    // start BatteryInfoService
                    Intent battery = new Intent(context, com.android.systemui.BatteryInfoService.class);
                    context.startService(battery);
                    sendIntentForEnergyBay(context);
            } catch (Exception e) {
                    Log.e(TAG, "Can't start load average service", e);
            }
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            Intent temp = new Intent();
            temp.setClass(context, EnergyBayActivity.class);
            temp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            temp.putExtra("action", action);
            context.startActivity(temp);
        }else if(ACTION_USB_HOST_FLAG_LOW_BATTERY.equals(action)) {
            // send intent ACTION_USB_HOST_FLAG_LOW_BATTERY
            sendIntentForLowBatteryUsbHost(context);
        }
    }
    /** LowBattery warning  when usb host on  **/
    private void sendIntentForLowBatteryUsbHost(Context mContext){
        boolean bon_usbhost = getBatteryUsbhostStatus(mContext) ;
        //Toast.makeText(mContext,"ACTION_USB_HOST_FLAG_LOW_BATTERY:bon_usbhost :" + bon_usbhost
        // ,Toast.LENGTH_SHORT).show();
        if(!bon_usbhost) return ;
        Intent temp = new Intent();
        temp.setClass(mContext, EnergyBayActivity.class);
        temp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        temp.putExtra("action", ACTION_USB_HOST_FLAG_LOW_BATTERY_WARNING);
        mContext.startActivity(temp);
    }
    private static final String KeyBatteryUsbhostStatus = "bBatteryUsbhostStatusKey" ;
    private final boolean getBatteryUsbhostStatus(Context mContext){
        int value = Settings.System.getInt(mContext.getContentResolver(),KeyBatteryUsbhostStatus,-1);
        return value == 1 ;
    }
    /** usb host for ACTION_BOOT_COMPLETED **/
    private void sendIntentForEnergyBay(Context mContext){
        UsbManager usbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String,UsbDevice> deviceList = usbManager.getDeviceList();
        //Log.d("WYQ","deviceList.size() = "+ deviceList.size());
        if(deviceList.size() < 1 ) return ;
        Intent temp = new Intent();
        temp.setClass(mContext, EnergyBayActivity.class);
        temp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        temp.putExtra("action", UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.startActivity(temp);
    }
}
