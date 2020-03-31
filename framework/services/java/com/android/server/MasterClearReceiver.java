/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;

import java.io.IOException;

public class MasterClearReceiver extends BroadcastReceiver {
    private static final String TAG = "MasterClear";
    private boolean mCopyLenovoResources=false; /* BLADEFHD-15, added by lenovo-sw liujg6, 20131018 */
    private boolean mFactoryWipeData=false; /* BLADEFHD-15, added by lenovo-sw liujg6, 20131018 */

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_REMOTE_INTENT)) {
            if (!"google.com".equals(intent.getStringExtra("from"))) {
                Slog.w(TAG, "Ignoring master clear request -- not from trusted server.");
                return;
            }
        }

        /* begin of BLADEFHD-15, added by lenovo-sw liujg6, 20131018 */
        if (intent.getAction().equals("android.intent.action.COPY_LENOVO_RESOURCES")) {
            mCopyLenovoResources=true;
        }else if (intent.getAction().equals("android.intent.action.FACTORY_WIPE_DATA")) {
            mFactoryWipeData=true;
         }
        /* end of BLADEFHD-15 */

        //check whether country code is set on ROW products, if not, need to prompt confirmation dialog before erase data
        //if(FeatureOption.MTK_SHARED_SDCARD) {
            boolean ignoreCheck = false;
            ignoreCheck = "true".equalsIgnoreCase(intent.getStringExtra("ignoreCheck"));
            String country = SystemProperties.get("ro.product.countrycode");
            if(!mCopyLenovoResources && !ignoreCheck && (country != null) && !country.isEmpty() && country.equalsIgnoreCase("cn")) {
                Slog.w(TAG, "Ignoring this master clear request -- country code was not set yet.");
                Thread thrConfirm = new Thread("ConfirmMasterClear") {
                    @Override
                    public void run() {
                        Intent i = new Intent("com.lenovo.intent.CONFIRM_MASTERCLEAR_WITHOUT_SETCC");
                        if(mFactoryWipeData) {
                            i.putExtra("isLenovoMasterClear", "true");
                        }
                        context.sendBroadcast(i);
                    }
                };
                thrConfirm.start();
                return;
            }
        //}

        Slog.w(TAG, "!!! FACTORY RESET !!!");
        // The reboot call is blocking, so we need to do it on another thread.
        Thread thr = new Thread("Reboot") {
            @Override
            public void run() {
                try {
                    /* begin of BLADEFHD-15, added by lenovo-sw liujg6, 20131018 */
                    if(mFactoryWipeData){
                        RecoverySystem.rebootFactoryWipeData(context);
                    }
                    else if(mCopyLenovoResources){
                        RecoverySystem.copyLenovoResources(context);
                    }
                    else{
                        RecoverySystem.rebootWipeUserData(context);
                    }
                    /* end of BLADEFHD-15 */
                    Log.wtf(TAG, "Still running after master clear?!");
                } catch (IOException e) {
                    Slog.e(TAG, "Can't perform master clear/factory reset", e);
                }
            }
        };
        thr.start();
    }
}
