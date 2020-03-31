/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2012-2014, The Linux Foundation. All rights reserved.
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

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IAlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
/*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
import android.content.IIntentSender;
/*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.util.TimeUtils;
/*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
import com.android.server.am.PendingIntentRecord;
/*[lenovo_fw,jiangzc1,lenovo_alarm], end*/

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TimeZone;

import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.AlarmManager.RTC;
import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.ELAPSED_REALTIME;
/*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
import static android.app.AlarmManager.RTC_ALARM;
import static android.app.AlarmManager.RTC_STARTUP;
import static android.app.AlarmManager.RTC_POWERON;
/*[lenovo_fw,jiangzc1,lenovo_alarm], end*/

/*[lenovo_fw,wangchao9,alarm_policy], "Refer to lenovo_fw.txt" , modify*/
import android.database.Cursor;
import java.util.List;
/*[lenovo_fw,wangchao9,alarm_policy], end*/
import com.android.internal.util.LocalLog;

class AlarmManagerService extends IAlarmManager.Stub {
    // The threshold for how long an alarm can be late before we print a
    // warning message.  The time duration is in milliseconds.
    private static final long LATE_ALARM_THRESHOLD = 10 * 1000;

    private static final int RTC_WAKEUP_MASK = 1 << RTC_WAKEUP;
    private static final int RTC_MASK = 1 << RTC;
    private static final int ELAPSED_REALTIME_WAKEUP_MASK = 1 << ELAPSED_REALTIME_WAKEUP; 
    private static final int ELAPSED_REALTIME_MASK = 1 << ELAPSED_REALTIME;
    //private static final int RTC_POWEROFF_WAKEUP_MASK = 1 << RTC_POWEROFF_WAKEUP;
    private static final int TIME_CHANGED_MASK = 1 << 16;
    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
    private static final int RTC_POWERON_MASK = 1 << AlarmManager.RTC_POWERON;
    private static final int RTC_ALARM_MASK = 1 << AlarmManager.RTC_ALARM;
    private static final int IS_WAKEUP_MASK = RTC_WAKEUP_MASK | ELAPSED_REALTIME_WAKEUP_MASK | RTC_POWERON_MASK | RTC_ALARM_MASK;
    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/

    // Mask for testing whether a given alarm type is wakeup vs non-wakeup
    private static final int TYPE_NONWAKEUP_MASK = 0x1; // low bit => non-wakeup

    private static final String TAG = "AlarmManager";
    private static final String ClockReceiver_TAG = "ClockReceiver";
    private static final boolean localLOGV = false;
    private static final boolean DEBUG_BATCH = localLOGV || false;
    private static final boolean DEBUG_VALIDATE = localLOGV || false;
    private static final int ALARM_EVENT = 1;
    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    
    private static final Intent mBackgroundIntent
            = new Intent().addFlags(Intent.FLAG_FROM_BACKGROUND);
    private static final IncreasingTimeOrder sIncreasingTimeOrder = new IncreasingTimeOrder();
    
    private static final boolean WAKEUP_STATS = false;

    private final Context mContext;

    private final LocalLog mLog = new LocalLog(TAG);

    private Object mLock = new Object();

    private final ArrayList<Integer> mTriggeredUids = new ArrayList<Integer>();
    private final ArrayList<Integer> mBlockedUids = new ArrayList<Integer>();

    private int mDescriptor;
    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
    private long mNextWakeup;
    private long mNextRtcWakeup;
    private long mNextNonWakeup;
    private long mNextRtcAlarm;
    private static boolean mRemoveRtcAlarm;
    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
    private int mBroadcastRefCount = 0;
    private PowerManager.WakeLock mWakeLock;
    private ArrayList<InFlight> mInFlight = new ArrayList<InFlight>();
    private final AlarmThread mWaitThread = new AlarmThread();
    private final AlarmHandler mHandler = new AlarmHandler();
    private ClockReceiver mClockReceiver;
    private UninstallReceiver mUninstallReceiver;
    //private QuickBootReceiver mQuickBootReceiver;
    private final ResultReceiver mResultReceiver = new ResultReceiver();
    private final PendingIntent mTimeTickSender;
    private final PendingIntent mDateChangeSender;

    class WakeupEvent {
        public long when;
        public int uid;
        public String action;

        public WakeupEvent(long theTime, int theUid, String theAction) {
            when = theTime;
            uid = theUid;
            action = theAction;
        }
    }

    private final LinkedList<WakeupEvent> mRecentWakeups = new LinkedList<WakeupEvent>();
    private final long RECENT_WAKEUP_PERIOD = 1000L * 60 * 60 * 24; // one day

    static final class Batch {
        long start;     // These endpoints are always in ELAPSED
        long end;
        boolean standalone; // certain "batches" don't participate in coalescing

        final ArrayList<Alarm> alarms = new ArrayList<Alarm>();

        Batch() {
            start = 0;
            end = Long.MAX_VALUE;
        }

        Batch(Alarm seed) {
            start = seed.whenElapsed;
            end = seed.maxWhen;
            alarms.add(seed);
        }

        int size() {
            return alarms.size();
        }

        Alarm get(int index) {
            return alarms.get(index);
        }

        long getWhenByElapsedTime(long whenElapsed) {
            for(int i=0;i< alarms.size();i++) {
                if(alarms.get(i).whenElapsed == whenElapsed)
                    return alarms.get(i).when;
            }

            return 0;
        }

        boolean canHold(long whenElapsed, long maxWhen) {
            return (end >= whenElapsed) && (start <= maxWhen);
        }

        boolean add(Alarm alarm) {
            boolean newStart = false;
            // narrows the batch if necessary; presumes that canHold(alarm) is true
            int index = Collections.binarySearch(alarms, alarm, sIncreasingTimeOrder);
            if (index < 0) {
                index = 0 - index - 1;
            }
            alarms.add(index, alarm);
            if (DEBUG_BATCH) {
                Slog.v(TAG, "Adding " + alarm + " to " + this);
            }
            if (alarm.whenElapsed > start) {
                start = alarm.whenElapsed;
                newStart = true;
            }
            if (alarm.maxWhen < end) {
                end = alarm.maxWhen;
            }

            if (DEBUG_BATCH) {
                Slog.v(TAG, "    => now " + this);
            }
            return newStart;
        }

        boolean remove(final PendingIntent operation) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                boolean canceled = false;
                Alarm alarm = alarms.get(i);
                IIntentSender sender = alarm.operation.getTarget();
                if (sender instanceof PendingIntentRecord) {
                	    PendingIntentRecord rec = (PendingIntentRecord)sender;
                	    canceled = rec.canceled();
                }
                if (alarm.operation.equals(operation) || canceled) {
                    alarms.remove(i);
                    didRemove = true;
                    if (alarm.type == RTC_ALARM || alarm.type == RTC_POWERON) {
                        mRemoveRtcAlarm = true;
                    }
                    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean remove(final String packageName) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (alarm.operation.getTargetPackage().equals(packageName)) {
                    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                    if (alarm.type == RTC_ALARM || alarm.type == RTC_POWERON) {
                        mRemoveRtcAlarm = true;
                    }
                    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
                    alarms.remove(i);
                    didRemove = true;
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean remove(final int userHandle) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (UserHandle.getUserId(alarm.operation.getCreatorUid()) == userHandle) {
                    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                    if (alarm.type == RTC_ALARM || alarm.type == RTC_POWERON) {
                        mRemoveRtcAlarm = true;
                    }
                    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
                    alarms.remove(i);
                    didRemove = true;
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean hasPackage(final String packageName) {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                if (a.operation.getTargetPackage().equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasWakeups() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // non-wakeup alarms are types 1 and 3, i.e. have the low bit set
                if ((a.type & TYPE_NONWAKEUP_MASK) == 0) {
                    return true;
                }
            }
            return false;
        }

        /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
        boolean hasRtcAlarms() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // rtc alarms are types 4 and 6
                if (a.type == RTC_ALARM || a.type == RTC_POWERON) {
                    return true;
                }
            }
            return false;
        }
        int rtcType() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // rtc alarms are types 4 and 6
                if (a.type == RTC_ALARM || a.type == RTC_POWERON) {
                    return a.type;
                }
            }
            return -1;
        }
        boolean alarmAndPowerOn() {
            boolean alarm = false;
            boolean powerOn = false;
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // rtc alarms are types 4 and 6
                if (a.type == RTC_ALARM) {
                    alarm = true;
                    break;
                }
            }
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // rtc alarms are types 4 and 6
                if (a.type == RTC_POWERON) {
                    powerOn = true;
                    break;
                }
            }
            return alarm && powerOn;
        }
        Alarm rtcAlarm() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // rtc alarms are types 4 and 6
                if (a.type == RTC_ALARM || a.type == RTC_POWERON) {
                    return a;
                }
            }
            return null;
        }
        /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(40);
            b.append("Batch{"); b.append(Integer.toHexString(this.hashCode()));
            b.append(" num="); b.append(size());
            b.append(" start="); b.append(start);
            b.append(" end="); b.append(end);
            if (standalone) {
                b.append(" STANDALONE");
            }
            b.append('}');
            return b.toString();
        }
    }

    static class BatchTimeOrder implements Comparator<Batch> {
        public int compare(Batch b1, Batch b2) {
            long when1 = b1.start;
            long when2 = b2.start;
            if (when1 - when2 > 0) {
                return 1;
            }
            if (when1 - when2 < 0) {
                return -1;
            }
            return 0;
        }
    }
    
    // minimum recurrence period or alarm futurity for us to be able to fuzz it
    private static final long MIN_FUZZABLE_INTERVAL = 10000;
    private static final BatchTimeOrder sBatchOrder = new BatchTimeOrder();
    private final ArrayList<Batch> mAlarmBatches = new ArrayList<Batch>();

    static long convertToElapsed(long when, int type) {
        /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
        final boolean isRtc = (type == RTC || type == RTC_WAKEUP || type == RTC_ALARM || type == RTC_POWERON);
        /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
        if (isRtc) {
            when -= System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }
        return when;
    }

    // Apply a heuristic to { recurrence interval, futurity of the trigger time } to
    // calculate the end of our nominal delivery window for the alarm.
    static long maxTriggerTime(long now, long triggerAtTime, long interval) {
        // Current heuristic: batchable window is 75% of either the recurrence interval
        // [for a periodic alarm] or of the time from now to the desired delivery time,
        // with a minimum delay/interval of 10 seconds, under which we will simply not
        // defer the alarm.
        long futurity = (interval == 0)
                ? (triggerAtTime - now)
                : interval;
        if (futurity < MIN_FUZZABLE_INTERVAL) {
            futurity = 0;
        }
        return triggerAtTime + (long)(.75 * futurity);
    }

    // returns true if the batch was added at the head
    static boolean addBatchLocked(ArrayList<Batch> list, Batch newBatch) {
        int index = Collections.binarySearch(list, newBatch, sBatchOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        list.add(index, newBatch);
        return (index == 0);
    }

    // Return the index of the matching batch, or -1 if none found.
    int attemptCoalesceLocked(long whenElapsed, long maxWhen) {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (!b.standalone && b.canHold(whenElapsed, maxWhen)) {
                return i;
            }
        }
        return -1;
    }

    // The RTC clock has moved arbitrarily, so we need to recalculate all the batching
    void rebatchAllAlarms() {
        synchronized (mLock) {
            rebatchAllAlarmsLocked(true);
        }
    }

    void rebatchAllAlarmsLocked(boolean doValidate) {
        ArrayList<Batch> oldSet = (ArrayList<Batch>) mAlarmBatches.clone();
        mAlarmBatches.clear();
        final long nowElapsed = SystemClock.elapsedRealtime();
        final int oldBatches = oldSet.size();
        for (int batchNum = 0; batchNum < oldBatches; batchNum++) {
            Batch batch = oldSet.get(batchNum);
            final int N = batch.size();
            for (int i = 0; i < N; i++) {
                Alarm a = batch.get(i);
                long whenElapsed = convertToElapsed(a.when, a.type);
                final long maxElapsed;
                if (a.whenElapsed == a.maxWhen) {
                    // Exact
                    maxElapsed = whenElapsed;
                } else {
                    // Not exact.  Preserve any explicit window, otherwise recalculate
                    // the window based on the alarm's new futurity.  Note that this
                    // reflects a policy of preferring timely to deferred delivery.
                    maxElapsed = (a.windowLength > 0)
                            ? (whenElapsed + a.windowLength)
                            : maxTriggerTime(nowElapsed, whenElapsed, a.repeatInterval);
                }
                setImplLocked(a.type, a.when, whenElapsed, a.windowLength, maxElapsed,
                        a.repeatInterval, a.operation, batch.standalone, doValidate, a.workSource);
            }
        }
    }

    private static final class InFlight extends Intent {
        final PendingIntent mPendingIntent;
        final WorkSource mWorkSource;
        final Pair<String, ComponentName> mTarget;
        final BroadcastStats mBroadcastStats;
        final FilterStats mFilterStats;
        final int mUid;

        InFlight(AlarmManagerService service, PendingIntent pendingIntent,
                      WorkSource workSource, int uid) {
            mPendingIntent = pendingIntent;
            mWorkSource = workSource;
            Intent intent = pendingIntent.getIntent();
            mTarget = intent != null
                    ? new Pair<String, ComponentName>(intent.getAction(), intent.getComponent())
                    : null;
            mBroadcastStats = service.getStatsLocked(pendingIntent);
            FilterStats fs = mBroadcastStats.filterStats.get(mTarget);
            if (fs == null) {
                fs = new FilterStats(mBroadcastStats, mTarget);
                mBroadcastStats.filterStats.put(mTarget, fs);
            }
            mFilterStats = fs;
            mUid = uid;
        }
    }

    private static final class FilterStats {
        final BroadcastStats mBroadcastStats;
        final Pair<String, ComponentName> mTarget;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;

        FilterStats(BroadcastStats broadcastStats, Pair<String, ComponentName> target) {
            mBroadcastStats = broadcastStats;
            mTarget = target;
        }
    }
    
    private static final class BroadcastStats {
        final String mPackageName;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;
        final HashMap<Pair<String, ComponentName>, FilterStats> filterStats
                = new HashMap<Pair<String, ComponentName>, FilterStats>();

        BroadcastStats(String packageName) {
            mPackageName = packageName;
        }
    }
    
    private final HashMap<String, BroadcastStats> mBroadcastStats
            = new HashMap<String, BroadcastStats>();
    
    public AlarmManagerService(Context context) {
        mContext = context;
        /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
        //mDescriptor = init();
        if ((SystemProperties.get("ro.boot.mode").equals("usb_cable")
                || SystemProperties.get("ro.boot.mode").equals("ac_charger")) &&
                !SystemProperties.get("sys.boot.mode", "unknown").equals("main")) {
            mDescriptor = -1;
        } else {
            mDescriptor = init();
        }
        mNextRtcAlarm = mNextWakeup = mNextNonWakeup = 0;
        /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/

        // We have to set current TimeZone info to kernel
        // because kernel doesn't keep this after reboot
        String tz = SystemProperties.get(TIMEZONE_PROPERTY);
        if (tz != null) {
            setTimeZone(tz);
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        
        mTimeTickSender = PendingIntent.getBroadcastAsUser(context, 0,
                new Intent(Intent.ACTION_TIME_TICK).addFlags(
                        Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND), 0,
                        UserHandle.ALL);
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        mDateChangeSender = PendingIntent.getBroadcastAsUser(context, 0, intent,
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, UserHandle.ALL);
        
        // now that we have initied the driver schedule the alarm
        mClockReceiver= new ClockReceiver();
        mClockReceiver.scheduleTimeTickEvent();
        mClockReceiver.scheduleDateChangedEvent();
        mUninstallReceiver = new UninstallReceiver();
        //mQuickBootReceiver = new QuickBootReceiver();
        
        if (mDescriptor != -1) {
            mWaitThread.start();
        } else {
            Slog.w(TAG, "Failed to open alarm driver. Falling back to a handler.");
        }
    }
    
    protected void finalize() throws Throwable {
        try {
            close(mDescriptor);
        } finally {
            super.finalize();
        }
    }

    @Override
    public void set(int type, long triggerAtTime, long windowLength, long interval,
            PendingIntent operation, WorkSource workSource) {
        if (workSource != null) {
            mContext.enforceCallingPermission(
                    android.Manifest.permission.UPDATE_DEVICE_STATS,
                    "AlarmManager.set");
        }

        set(type, triggerAtTime, windowLength, interval, operation, false, workSource);
    }

    public void set(int type, long triggerAtTime, long windowLength, long interval,
            PendingIntent operation, boolean isStandalone, WorkSource workSource) {
        if (operation == null) {
            Slog.w(TAG, "set/setRepeating ignored because there is no intent");
            return;
        }

        // Sanity check the window length.  This will catch people mistakenly
        // trying to pass an end-of-window timestamp rather than a duration.
        if (windowLength > AlarmManager.INTERVAL_HALF_DAY) {
            Slog.w(TAG, "Window length " + windowLength
                    + "ms suspiciously long; limiting to 1 hour");
            windowLength = AlarmManager.INTERVAL_HOUR;
        }

        /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
        String pkg = operation.getTargetPackage();
        if (CANDIDATE_PACKAGES.contains(pkg)
                && (type == AlarmManager.RTC_WAKEUP)) {
            Slog.i(TAG, "use RTC_ALARM for " + pkg);
            type = AlarmManager.RTC_ALARM;
            windowLength = AlarmManager.WINDOW_EXACT;
        }
        //if (type < RTC_WAKEUP || type > ELAPSED_REALTIME) {
        if (type < RTC_WAKEUP || type > RTC_POWERON) {
        /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
            throw new IllegalArgumentException("Invalid alarm type " + type);
        }

        /*[lenovo_fw,wangchao9,alarm_policy], "Refer to lenovo_fw.txt" , modify*/
        if ((type == RTC_WAKEUP || type == ELAPSED_REALTIME_WAKEUP)&& !pkgInWhitelist(operation.getCreatorPackage())) {
            type ++;
        }
        /*[lenovo_fw,wangchao9,alarm_policy], end*/

        if (triggerAtTime < 0) {
            final long who = Binder.getCallingUid();
            final long what = Binder.getCallingPid();
            Slog.w(TAG, "Invalid alarm trigger time! " + triggerAtTime + " from uid=" + who
                    + " pid=" + what);
            triggerAtTime = 0;
        }

        final long nowElapsed = SystemClock.elapsedRealtime();
        final long triggerElapsed = convertToElapsed(triggerAtTime, type);
        final long maxElapsed;
        if (windowLength == AlarmManager.WINDOW_EXACT) {
            maxElapsed = triggerElapsed;
        } else if (windowLength < 0) {
            maxElapsed = maxTriggerTime(nowElapsed, triggerElapsed, interval);
        } else {
            maxElapsed = triggerElapsed + windowLength;
        }

        synchronized (mLock) {
            if (true) {
                Slog.v(TAG, "set(" + operation + ") : type=" + type
                        + " triggerAtTime=" + triggerAtTime + " win=" + windowLength
                        + " tElapsed=" + triggerElapsed + " maxElapsed=" + maxElapsed
                        + " interval=" + interval + " standalone=" + isStandalone);
            }
            setImplLocked(type, triggerAtTime, triggerElapsed, windowLength, maxElapsed,
                    interval, operation, isStandalone, true, workSource);
        }
    }

    private void setImplLocked(int type, long when, long whenElapsed, long windowLength,
            long maxWhen, long interval, PendingIntent operation, boolean isStandalone,
            boolean doValidate, WorkSource workSource) {
        Alarm a = new Alarm(type, when, whenElapsed, windowLength, maxWhen, interval,
                operation, workSource);
        removeLocked(operation);

        int whichBatch = (isStandalone) ? -1 : attemptCoalesceLocked(whenElapsed, maxWhen);
        if (whichBatch < 0) {
            Batch batch = new Batch(a);
            batch.standalone = isStandalone;
            addBatchLocked(mAlarmBatches, batch);
        } else {
            Batch batch = mAlarmBatches.get(whichBatch);
            if (batch.add(a)) {
                // The start time of this batch advanced, so batch ordering may
                // have just been broken.  Move it to where it now belongs.
                mAlarmBatches.remove(whichBatch);
                addBatchLocked(mAlarmBatches, batch);
            }
            /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
            //Slog.w(TAG, "remove poweron: " + batch.toString() + "mNextRtcAlarm:" + mNextRtcAlarm);
            if (batch.alarmAndPowerOn() && mNextRtcAlarm == batch.start) {
                Slog.w(TAG, "remove poweron");
                mNextRtcAlarm = 0;
            }
            /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
        }

        if (DEBUG_VALIDATE) {
            if (doValidate && !validateConsistencyLocked()) {
                Slog.v(TAG, "Tipping-point operation: type=" + type + " when=" + when
                        + " when(hex)=" + Long.toHexString(when)
                        + " whenElapsed=" + whenElapsed + " maxWhen=" + maxWhen
                        + " interval=" + interval + " op=" + operation
                        + " standalone=" + isStandalone);
                rebatchAllAlarmsLocked(false);
            }
        }

        rescheduleKernelAlarmsLocked();
        Slog.w(TAG, "setImplLocked finished!");
    }

    private void logBatchesLocked() {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(2048);
        PrintWriter pw = new PrintWriter(bs);
        final long nowRTC = System.currentTimeMillis();
        final long nowELAPSED = SystemClock.elapsedRealtime();
        final int NZ = mAlarmBatches.size();
        for (int iz = 0; iz < NZ; iz++) {
            Batch bz = mAlarmBatches.get(iz);
            pw.append("Batch "); pw.print(iz); pw.append(": "); pw.println(bz);
            dumpAlarmList(pw, bz.alarms, "  ", nowELAPSED, nowRTC);
            pw.flush();
            Slog.v(TAG, bs.toString());
            bs.reset();
        }
    }

    private boolean validateConsistencyLocked() {
        if (DEBUG_VALIDATE) {
            long lastTime = Long.MIN_VALUE;
            final int N = mAlarmBatches.size();
            for (int i = 0; i < N; i++) {
                Batch b = mAlarmBatches.get(i);
                if (b.start >= lastTime) {
                    // duplicate start times are okay because of standalone batches
                    lastTime = b.start;
                } else {
                    Slog.e(TAG, "CONSISTENCY FAILURE: Batch " + i + " is out of order");
                    logBatchesLocked();
                    return false;
                }
            }
        }
        return true;
    }

    private Batch findFirstWakeupBatchLocked() {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasWakeups()) {
                return b;
            }
        }
        return null;
    }

    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
    private Batch findFirstRtcAlarmBatchLocked() {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasRtcAlarms()) {
                return b;
            }
        }
        return null;
    }
    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
    private void rescheduleKernelAlarmsLocked() {
        // Schedule the next upcoming wakeup alarm.  If there is a deliverable batch
        // prior to that which contains no wakeups, we schedule that as well.
        if (mAlarmBatches.size() > 0) {
            final Batch firstWakeup = findFirstWakeupBatchLocked();
            final Batch firstBatch = mAlarmBatches.get(0);
            /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
            final Batch firstRtcAlarm = findFirstRtcAlarmBatchLocked();
            if (null == firstRtcAlarm && mRemoveRtcAlarm) {
                Slog.i(TAG, "no rtc alarms");
                SystemProperties.set(RTC_ALARM_TIME_PROPERTY, Long.toString(0));
                SystemProperties.set(RTC_START_TIME_PROPERTY, Long.toString(0));
                if (-1 != mDescriptor) {
                    setLocked(AlarmManager.RTC_POWERON, 0);
                    mNextRtcAlarm = 0;
                }
                mRemoveRtcAlarm = false;
            }
            /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
            /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
            if (firstRtcAlarm != null && mNextRtcAlarm != firstRtcAlarm.start) {
                mNextRtcAlarm = firstRtcAlarm.start;
                setLocked(firstRtcAlarm.rtcType(), firstRtcAlarm.rtcAlarm().when);
                if (firstRtcAlarm.alarmAndPowerOn()) {
                    SystemProperties.set(RTC_ALARM_TIME_PROPERTY, Long.toString(firstRtcAlarm.rtcAlarm().when));
                }
            }
            if (firstWakeup != null && mNextWakeup != firstWakeup.start) {
                if (!firstWakeup.hasRtcAlarms()) {
                    mNextWakeup = firstWakeup.start;
                    setLocked(ELAPSED_REALTIME_WAKEUP, firstWakeup.start);
                }
            }
            if (firstBatch != null && firstBatch != firstWakeup && firstBatch != firstRtcAlarm && mNextNonWakeup != firstBatch.start) {
                mNextNonWakeup = firstBatch.start;
                setLocked(ELAPSED_REALTIME, firstBatch.start);
            }
            /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
        }
    }

    public void setTime(long millis) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME",
                "setTime");

        SystemClock.setCurrentTimeMillis(millis);
    }

    public void setTimeZone(String tz) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME_ZONE",
                "setTimeZone");

        long oldId = Binder.clearCallingIdentity();
        try {
            if (TextUtils.isEmpty(tz)) return;
            TimeZone zone = TimeZone.getTimeZone(tz);
            // Prevent reentrant calls from stepping on each other when writing
            // the time zone property
            boolean timeZoneWasChanged = false;
            synchronized (this) {
                String current = SystemProperties.get(TIMEZONE_PROPERTY);
                if (current == null || !current.equals(zone.getID())) {
                    if (localLOGV) {
                        Slog.v(TAG, "timezone changed: " + current + ", new=" + zone.getID());
                    }
                    timeZoneWasChanged = true;
                    SystemProperties.set(TIMEZONE_PROPERTY, zone.getID());
                }

                // Update the kernel timezone information
                // Kernel tracks time offsets as 'minutes west of GMT'
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                //setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
                if (-1 != mDescriptor) {
                    setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
                }
                /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
            }

            TimeZone.setDefault(null);

            if (timeZoneWasChanged) {
                Intent intent = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.putExtra("time-zone", zone.getID());
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        } finally {
            Binder.restoreCallingIdentity(oldId);
        }
    }
    
    public void remove(PendingIntent operation) {
        if (operation == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(operation);
        }
    }

    /* updates the blocked uids, so if a wake lock is acquired to only fire
     * alarm for it, it can be released.
     */
    void updateBlockedUids(int uid, boolean isBlocked) {
        if (localLOGV) Slog.v(TAG, "UpdateBlockedUids: uid = "+uid +"isBlocked = "+isBlocked);
        synchronized(mLock) {
            if(isBlocked) {
                for( int i=0; i < mTriggeredUids.size(); i++) {
                    if(mTriggeredUids.contains(new Integer(uid))) {
                        if (localLOGV) {
                            Slog.v(TAG,"TriggeredUids has this uid, mBroadcastRefCount="
                                +mBroadcastRefCount);
                        }
                        mTriggeredUids.remove(new Integer(uid));
                        mBlockedUids.add(new Integer(uid));
                        if(mBroadcastRefCount > 0){
                            mBroadcastRefCount--;
                            if (mBroadcastRefCount == 0) {
                                /* all the uids for which the alarms are triggered
                                 * are either blocked or have called onSendFinished.
                                */
                                mWakeLock.release();
                                if (localLOGV) Slog.v(TAG, "AM WakeLock Released Internally");
                            }
                        } else {
                            if (localLOGV) {
                                Slog.v(TAG, "Trying to decrement mBroadcastRefCount past zero");
                            }
                        }
                    } else {
                        //no more matching uids break from the for loop
                        break;
                    }
                }
            } else {
                for(int i = 0; i < mBlockedUids.size(); i++) {
                    if(!mBlockedUids.remove(new Integer(uid))) {
                        //no more matching uids break from the for loop
                        break;
                     }
                }
            }
        }
    }

    public void removeLocked(PendingIntent operation) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            /*ArrayList<Alarm> alarmList = b.alarms;
            Alarm alarm = null;
            for (int j = alarmList.size() - 1; j >= 0; j--) {
                alarm = alarmList.get(j);
                if (alarm.type == RTC_POWEROFF_WAKEUP && alarm.operation.equals(operation)) {
                    long alarmSeconds, alarmNanoseconds;
                    alarmSeconds = alarm.when / 1000;
                    alarmNanoseconds = (alarm.when % 1000) * 1000 * 1000;
                    Slog.w(TAG,"Clear alarm type=" + alarm.type + ",alarmSeconds=" +
                       alarmSeconds);
                    clear(mDescriptor, alarm.type, alarmSeconds, alarmNanoseconds);
                    mNextRtcWakeup = 0;
                }
            }*/
            didRemove |= b.remove(operation);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(operation) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public void removeLocked(String packageName) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(packageName);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(package) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public void removeUserLocked(int userHandle) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(userHandle);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(user) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public boolean lookForPackageLocked(String packageName) {
        for (int i = 0; i < mAlarmBatches.size(); i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasPackage(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void setLocked(int type, long when)
    {
        if (mDescriptor != -1)
        {
            /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
            if (type == AlarmManager.RTC_ALARM
                    || type == AlarmManager.RTC_POWERON) {
                SystemProperties.set(type == AlarmManager.RTC_ALARM ? RTC_ALARM_TIME_PROPERTY : RTC_START_TIME_PROPERTY,
                            Long.toString(when));
                type = AlarmManager.RTC_POWERON;
            }
            /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
            // The kernel never triggers alarms with negative wakeup times
            // so we ensure they are positive.
            long alarmSeconds, alarmNanoseconds;
            if (when < 0) {
                alarmSeconds = 0;
                alarmNanoseconds = 0;
            } else {
                alarmSeconds = when / 1000;
                alarmNanoseconds = (when % 1000) * 1000 * 1000;
                /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                //rtc only ensure second
                if (type == AlarmManager.RTC_POWERON) {
                    alarmNanoseconds = 0;
                    alarmSeconds ++;
                }
                /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
            }
            Slog.v(TAG, "Set alarm type: " + type + " alarmSeconds: " + alarmSeconds + " alarmNanoseconds: " + alarmNanoseconds);
            set(mDescriptor, type, alarmSeconds, alarmNanoseconds);
        }
        else
        {
            Message msg = Message.obtain();
            msg.what = ALARM_EVENT;
            
            mHandler.removeMessages(ALARM_EVENT);
            mHandler.sendMessageAtTime(msg, when);
        }
    }
    
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump AlarmManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        
        synchronized (mLock) {
            pw.println("Current Alarm Manager state:");
            final long nowRTC = System.currentTimeMillis();
            final long nowELAPSED = SystemClock.elapsedRealtime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            pw.print("nowRTC="); pw.print(nowRTC);
            pw.print("="); pw.print(sdf.format(new Date(nowRTC)));
            pw.print(" nowELAPSED="); pw.println(nowELAPSED);

            long nextWakeupRTC = mNextWakeup + (nowRTC - nowELAPSED);
            long nextNonWakeupRTC = mNextNonWakeup + (nowRTC - nowELAPSED);
            pw.print("Next alarm: "); pw.print(mNextNonWakeup);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextNonWakeupRTC)));
            pw.print("Next wakeup: "); pw.print(mNextWakeup);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextWakeupRTC)));

            if (mAlarmBatches.size() > 0) {
                pw.println();
                pw.print("Pending alarm batches: ");
                pw.println(mAlarmBatches.size());
                for (Batch b : mAlarmBatches) {
                    pw.print(b); pw.println(':');
                    dumpAlarmList(pw, b.alarms, "  ", nowELAPSED, nowRTC);
                }
            }

            pw.println();
            pw.print("  Broadcast ref count: "); pw.println(mBroadcastRefCount);
            pw.println();

            if (mLog.dump(pw, "  Recent problems", "    ")) {
                pw.println();
            }

            final FilterStats[] topFilters = new FilterStats[10];
            final Comparator<FilterStats> comparator = new Comparator<FilterStats>() {
                @Override
                public int compare(FilterStats lhs, FilterStats rhs) {
                    if (lhs.aggregateTime < rhs.aggregateTime) {
                        return 1;
                    } else if (lhs.aggregateTime > rhs.aggregateTime) {
                        return -1;
                    }
                    return 0;
                }
            };
            int len = 0;
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    FilterStats fs = fe.getValue();
                    int pos = len > 0
                            ? Arrays.binarySearch(topFilters, 0, len, fs, comparator) : 0;
                    if (pos < 0) {
                        pos = -pos - 1;
                    }
                    if (pos < topFilters.length) {
                        int copylen = topFilters.length - pos - 1;
                        if (copylen > 0) {
                            System.arraycopy(topFilters, pos, topFilters, pos+1, copylen);
                        }
                        topFilters[pos] = fs;
                        if (len < topFilters.length) {
                            len++;
                        }
                    }
                }
            }
            if (len > 0) {
                pw.println("  Top Alarms:");
                for (int i=0; i<len; i++) {
                    FilterStats fs = topFilters[i];
                    pw.print("    ");
                    if (fs.nesting > 0) pw.print("*ACTIVE* ");
                    TimeUtils.formatDuration(fs.aggregateTime, pw);
                    pw.print(" running, "); pw.print(fs.numWakeup);
                    pw.print(" wakeups, "); pw.print(fs.count);
                    pw.print(" alarms: "); pw.print(fs.mBroadcastStats.mPackageName);
                    pw.println();
                    pw.print("      ");
                    if (fs.mTarget.first != null) {
                        pw.print(" act="); pw.print(fs.mTarget.first);
                    }
                    if (fs.mTarget.second != null) {
                        pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                    }
                    pw.println();
                }
            }

            pw.println(" ");
            pw.println("  Alarm Stats:");
            final ArrayList<FilterStats> tmpFilters = new ArrayList<FilterStats>();
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                pw.print("  ");
                if (bs.nesting > 0) pw.print("*ACTIVE* ");
                pw.print(be.getKey());
                pw.print(" "); TimeUtils.formatDuration(bs.aggregateTime, pw);
                        pw.print(" running, "); pw.print(bs.numWakeup);
                        pw.println(" wakeups:");
                tmpFilters.clear();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    tmpFilters.add(fe.getValue());
                }
                Collections.sort(tmpFilters, comparator);
                for (int i=0; i<tmpFilters.size(); i++) {
                    FilterStats fs = tmpFilters.get(i);
                    pw.print("    ");
                            if (fs.nesting > 0) pw.print("*ACTIVE* ");
                            TimeUtils.formatDuration(fs.aggregateTime, pw);
                            pw.print(" "); pw.print(fs.numWakeup);
                            pw.print(" wakes " ); pw.print(fs.count);
                            pw.print(" alarms:");
                            if (fs.mTarget.first != null) {
                                pw.print(" act="); pw.print(fs.mTarget.first);
                            }
                            if (fs.mTarget.second != null) {
                                pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                            }
                            pw.println();
                }
            }

            if (WAKEUP_STATS) {
                pw.println();
                pw.println("  Recent Wakeup History:");
                long last = -1;
                for (WakeupEvent event : mRecentWakeups) {
                    pw.print("    "); pw.print(sdf.format(new Date(event.when)));
                    pw.print('|');
                    if (last < 0) {
                        pw.print('0');
                    } else {
                        pw.print(event.when - last);
                    }
                    last = event.when;
                    pw.print('|'); pw.print(event.uid);
                    pw.print('|'); pw.print(event.action);
                    pw.println();
                }
                pw.println();
            }
        }
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, String label, long now) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", now);
        }
    }

    private static final String labelForType(int type) {
        switch (type) {
        case RTC: return "RTC";
        case RTC_WAKEUP : return "RTC_WAKEUP";
        case ELAPSED_REALTIME : return "ELAPSED";
        case ELAPSED_REALTIME_WAKEUP: return "ELAPSED_WAKEUP";
        /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
        case AlarmManager.RTC_ALARM: return "RTC_ALARM";
        case AlarmManager.RTC_POWERON: return "RTC_POWERON";
        /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
        default:
            break;
        }
        return "--unknown--";
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, long nowELAPSED, long nowRTC) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            final String label = labelForType(a.type);
            long now = (a.type <= RTC) ? nowRTC : nowELAPSED;
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", now);
        }
    }

    private native int init();
    private native void close(int fd);
    private native void set(int fd, int type, long seconds, long nanoseconds);
    private native void clear(int fd, int type, long seconds, long nanoseconds);
    private native int waitForAlarm(int fd);
    private native int setKernelTimezone(int fd, int minuteswest);

    private void triggerAlarmsLocked(ArrayList<Alarm> triggerList, long nowELAPSED, long nowRTC) {
        // batches are temporally sorted, so we need only pull from the
        // start of the list until we either empty it or hit a batch
        // that is not yet deliverable
        while (mAlarmBatches.size() > 0) {
            Batch batch = mAlarmBatches.get(0);
            if (batch.start > nowELAPSED) {
                // Everything else is scheduled for the future
                Slog.w(TAG, "triggerAlarmsLocked: head of list: " + batch.start + " nowELAPSED: " + nowELAPSED);
                break;
            }

            // We will (re)schedule some alarms now; don't let that interfere
            // with delivery of this current batch
            mAlarmBatches.remove(0);

            final int N = batch.size();
            for (int i = 0; i < N; i++) {
                Alarm alarm = batch.get(i);
                alarm.count = 1;
                triggerList.add(alarm);

                // Recurring alarms may have passed several alarm intervals while the
                // phone was asleep or off, so pass a trigger count when sending them.
                if (alarm.repeatInterval > 0) {
                    // this adjustment will be zero if we're late by
                    // less than one full repeat interval
                    alarm.count += (nowELAPSED - alarm.whenElapsed) / alarm.repeatInterval;

                    // Also schedule its next recurrence
                    final long delta = alarm.count * alarm.repeatInterval;
                    final long nextElapsed = alarm.whenElapsed + delta;
                    setImplLocked(alarm.type, alarm.when + delta, nextElapsed, alarm.windowLength,
                            maxTriggerTime(nowELAPSED, nextElapsed, alarm.repeatInterval),
                            alarm.repeatInterval, alarm.operation, batch.standalone, true,
                            alarm.workSource);
                }

            }
        }
    }

    /**
     * This Comparator sorts Alarms into increasing time order.
     */
    public static class IncreasingTimeOrder implements Comparator<Alarm> {
        public int compare(Alarm a1, Alarm a2) {
            long when1 = a1.when;
            long when2 = a2.when;
            if (when1 - when2 > 0) {
                return 1;
            }
            if (when1 - when2 < 0) {
                return -1;
            }
            return 0;
        }
    }
    
    private static class Alarm {
        public int type;
        public int count;
        public long when;
        public long windowLength;
        public long whenElapsed;    // 'when' in the elapsed time base
        public long maxWhen;        // also in the elapsed time base
        public long repeatInterval;
        public PendingIntent operation;
        public WorkSource workSource;
        public int uid;
        public int pid;

        
        public Alarm(int _type, long _when, long _whenElapsed, long _windowLength, long _maxWhen,
                long _interval, PendingIntent _op, WorkSource _ws) {
            type = _type;
            when = _when;
            whenElapsed = _whenElapsed;
            windowLength = _windowLength;
            maxWhen = _maxWhen;
            repeatInterval = _interval;
            operation = _op;
            workSource = _ws;
            uid = Binder.getCallingUid();
            pid = Binder.getCallingPid();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Alarm{");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" type ");
            sb.append(type);
            sb.append(" ");
            sb.append(operation.getTargetPackage());
            sb.append('}');
            return sb.toString();
        }

        public void dump(PrintWriter pw, String prefix, long now) {
            pw.print(prefix); pw.print("type="); pw.print(type);
                    pw.print(" whenElapsed="); pw.print(whenElapsed);
                    pw.print(" when="); TimeUtils.formatDuration(when, now, pw);
                    pw.print(" window="); pw.print(windowLength);
                    pw.print(" repeatInterval="); pw.print(repeatInterval);
                    pw.print(" count="); pw.println(count);
            pw.print(prefix); pw.print("operation="); pw.println(operation);
        }
    }

    void recordWakeupAlarms(ArrayList<Batch> batches, long nowELAPSED, long nowRTC) {
        final int numBatches = batches.size();
        for (int nextBatch = 0; nextBatch < numBatches; nextBatch++) {
            Batch b = batches.get(nextBatch);
            if (b.start > nowELAPSED) {
                break;
            }

            final int numAlarms = b.alarms.size();
            for (int nextAlarm = 0; nextAlarm < numAlarms; nextAlarm++) {
                Alarm a = b.alarms.get(nextAlarm);
                WakeupEvent e = new WakeupEvent(nowRTC,
                        a.operation.getCreatorUid(),
                        a.operation.getIntent().getAction());
                mRecentWakeups.add(e);
            }
        }
    }

    private void filtQuickBootAlarms(ArrayList<Alarm> triggerList) {

        for (int i = triggerList.size() - 1; i >= 0; i--) {
            Alarm alarm = triggerList.get(i);

            // bypass system alarms
            if (!"android".equals(alarm.operation.getTargetPackage())) {
                triggerList.remove(i);
                Slog.v(TAG, "ignore -> " + alarm.operation.getTargetPackage());
            }
        }
    }

    private class AlarmThread extends Thread
    {
        public AlarmThread()
        {
            super("AlarmManager");
        }
        
        public void run()
        {
            ArrayList<Alarm> triggerList = new ArrayList<Alarm>();

            while (true)
            {
                int result = waitForAlarm(mDescriptor);

                triggerList.clear();

                if ((result & TIME_CHANGED_MASK) != 0) {
                    if (DEBUG_BATCH) {
                        Slog.v(TAG, "Time changed notification from kernel; rebatching");
                    }
                    remove(mTimeTickSender);
                    rebatchAllAlarms();
                    mClockReceiver.scheduleTimeTickEvent();
                    Intent intent = new Intent(Intent.ACTION_TIME_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                            | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                }
                
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    if (localLOGV) Slog.v(
                        TAG, "Checking for alarms... rtc=" + nowRTC
                        + ", elapsed=" + nowELAPSED);

                    if (WAKEUP_STATS) {
                        if ((result & IS_WAKEUP_MASK) != 0) {
                            long newEarliest = nowRTC - RECENT_WAKEUP_PERIOD;
                            int n = 0;
                            for (WakeupEvent event : mRecentWakeups) {
                                if (event.when > newEarliest) break;
                                n++; // number of now-stale entries at the list head
                            }
                            for (int i = 0; i < n; i++) {
                                mRecentWakeups.remove();
                            }

                            recordWakeupAlarms(mAlarmBatches, nowELAPSED, nowRTC);
                        }
                    }
                    triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                    rescheduleKernelAlarmsLocked();

                    /*if (SystemProperties.getInt("sys.quickboot.enable", 0) == 1) {
                        filtQuickBootAlarms(triggerList);
                    }*/

                    // now deliver the alarm intents
                    for (int i=0; i<triggerList.size(); i++) {
                        Alarm alarm = triggerList.get(i);
                        try {
                            if (true) Slog.v(TAG, "sending alarm " + alarm);
                            alarm.operation.send(mContext, 0,
                                    mBackgroundIntent.putExtra(
                                            Intent.EXTRA_ALARM_COUNT, alarm.count),
                                    mResultReceiver, mHandler);
                            
                            // we have an active broadcast so stay awake.
                            if (mBroadcastRefCount == 0) {
                                setWakelockWorkSource(alarm.operation, alarm.workSource);
                                mWakeLock.acquire();
                            }
                            final InFlight inflight = new InFlight(AlarmManagerService.this,
                                    alarm.operation, alarm.workSource, alarm.uid);
                            mInFlight.add(inflight);
                            mBroadcastRefCount++;
                            mTriggeredUids.add(new Integer(alarm.uid));
                            final BroadcastStats bs = inflight.mBroadcastStats;
                            bs.count++;
                            if (bs.nesting == 0) {
                                bs.nesting = 1;
                                bs.startTime = nowELAPSED;
                            } else {
                                bs.nesting++;
                            }
                            final FilterStats fs = inflight.mFilterStats;
                            fs.count++;
                            if (fs.nesting == 0) {
                                fs.nesting = 1;
                                fs.startTime = nowELAPSED;
                            } else {
                                fs.nesting++;
                            }
                            /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                            /*if (alarm.type == ELAPSED_REALTIME_WAKEUP
                                    || alarm.type == RTC_WAKEUP) {*/
                            if (alarm.type == AlarmManager.ELAPSED_REALTIME_WAKEUP
                                    || alarm.type == AlarmManager.RTC_WAKEUP
                                    || alarm.type == AlarmManager.RTC_POWERON) {
                            /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
                                bs.numWakeup++;
                                fs.numWakeup++;
                                ActivityManagerNative.noteWakeupAlarm(
                                        alarm.operation);
                            }
                        } catch (PendingIntent.CanceledException e) {
                            if (alarm.repeatInterval > 0) {
                                // This IntentSender is no longer valid, but this
                                // is a repeating alarm, so toss the hoser.
                                remove(alarm.operation);
                            }
                        } catch (RuntimeException e) {
                            Slog.w(TAG, "Failure sending alarm.", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Attribute blame for a WakeLock.
     * @param pi PendingIntent to attribute blame to if ws is null.
     * @param ws WorkSource to attribute blame.
     */
    void setWakelockWorkSource(PendingIntent pi, WorkSource ws) {
        try {
            if (ws != null) {
                mWakeLock.setWorkSource(ws);
                return;
            }

            final int uid = ActivityManagerNative.getDefault()
                    .getUidForIntentSender(pi.getTarget());
            if (uid >= 0) {
                mWakeLock.setWorkSource(new WorkSource(uid));
                return;
            }
            // Something went wrong; fall back to attributing the lock to the OS
            mWakeLock.setWorkSource(null);
        } catch (Exception e) {
        }
    }

    private class AlarmHandler extends Handler {
        public static final int ALARM_EVENT = 1;
        public static final int MINUTE_CHANGE_EVENT = 2;
        public static final int DATE_CHANGE_EVENT = 3;
        
        public AlarmHandler() {
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == ALARM_EVENT) {
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                }
                
                // now trigger the alarms without the lock held
                for (int i=0; i<triggerList.size(); i++) {
                    Alarm alarm = triggerList.get(i);
                    try {
                        alarm.operation.send();
                    } catch (PendingIntent.CanceledException e) {
                        if (alarm.repeatInterval > 0) {
                            // This IntentSender is no longer valid, but this
                            // is a repeating alarm, so toss the hoser.
                            remove(alarm.operation);
                        }
                    }
                }
            }
        }
    }

    private class QuickBootReceiver extends BroadcastReceiver {

        public QuickBootReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("org.codeaurora.quickboot.appkilled");
            mContext.registerReceiver(this, filter,
                    "android.permission.DEVICE_POWER", null);
        }

        @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String pkgList[] = null;
                if ("org.codeaurora.quickboot.appkilled".equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    if (pkgList != null && (pkgList.length > 0)) {
                        for (String pkg : pkgList) {
                            removeLocked(pkg);
                            mBroadcastStats.remove(pkg);
                        }
                    }
                }
            }
    }

    class ClockReceiver extends BroadcastReceiver {
        public ClockReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                if (true) {
                    Slog.v(TAG, "Received TIME_TICK alarm; rescheduling");
                }
                scheduleTimeTickEvent();
            } else if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED)) {
                // Since the kernel does not keep track of DST, we need to
                // reset the TZ information at the beginning of each day
                // based off of the current Zone gmt offset + userspace tracked
                // daylight savings information.
                TimeZone zone = TimeZone.getTimeZone(SystemProperties.get(TIMEZONE_PROPERTY));
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
                //setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
                if (-1 != mDescriptor) {
                    setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
                }
                /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/
                scheduleDateChangedEvent();
            }
        }
        
        public void scheduleTimeTickEvent() {
            final long currentTime = System.currentTimeMillis();
            final long nextTime = 60000 * ((currentTime / 60000) + 1);

            // Schedule this event for the amount of time that it would take to get to
            // the top of the next minute.
            final long tickEventDelay = nextTime - currentTime;

            final WorkSource workSource = null; // Let system take blame for time tick events.
            set(ELAPSED_REALTIME, SystemClock.elapsedRealtime() + tickEventDelay, 0,
                    0, mTimeTickSender, true, workSource);
        }

        public void scheduleDateChangedEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_MONTH, 1);

            final WorkSource workSource = null; // Let system take blame for date change events.
            set(RTC, calendar.getTimeInMillis(), 0, 0, mDateChangeSender, true, workSource);
        }
    }
    
    class UninstallReceiver extends BroadcastReceiver {
        public UninstallReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
            filter.addDataScheme("package");
            mContext.registerReceiver(this, filter);
             // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            sdFilter.addAction(Intent.ACTION_USER_STOPPED);
            mContext.registerReceiver(this, sdFilter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                String action = intent.getAction();
                String pkgList[] = null;
                if (Intent.ACTION_QUERY_PACKAGE_RESTART.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    for (String packageName : pkgList) {
                        if (lookForPackageLocked(packageName)) {
                            setResultCode(Activity.RESULT_OK);
                            return;
                        }
                    }
                    return;
                } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                } else if (Intent.ACTION_USER_STOPPED.equals(action)) {
                    int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    if (userHandle >= 0) {
                        removeUserLocked(userHandle);
                    }
                } else {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                            && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // This package is being updated; don't kill its alarms.
                        return;
                    }
                    Uri data = intent.getData();
                    if (data != null) {
                        String pkg = data.getSchemeSpecificPart();
                        if (pkg != null) {
                            pkgList = new String[]{pkg};
                        }
                    }
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkg : pkgList) {
                        removeLocked(pkg);
                        mBroadcastStats.remove(pkg);
                    }
                }
            }
        }
    }
    
    private final BroadcastStats getStatsLocked(PendingIntent pi) {
        String pkg = pi.getTargetPackage();
        BroadcastStats bs = mBroadcastStats.get(pkg);
        if (bs == null) {
            bs = new BroadcastStats(pkg);
            mBroadcastStats.put(pkg, bs);
        }
        return bs;
    }

    class ResultReceiver implements PendingIntent.OnFinished {
        public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            synchronized (mLock) {
                int uid = 0;
                InFlight inflight = null;
                for (int i=0; i<mInFlight.size(); i++) {
                    if (mInFlight.get(i).mPendingIntent == pi) {
                        uid = mInFlight.get(i).mUid;
                        inflight = mInFlight.remove(i);
                        break;
                    }
                }
                if (inflight != null) {
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    BroadcastStats bs = inflight.mBroadcastStats;
                    bs.nesting--;
                    if (bs.nesting <= 0) {
                        bs.nesting = 0;
                        bs.aggregateTime += nowELAPSED - bs.startTime;
                    }
                    FilterStats fs = inflight.mFilterStats;
                    fs.nesting--;
                    if (fs.nesting <= 0) {
                        fs.nesting = 0;
                        fs.aggregateTime += nowELAPSED - fs.startTime;
                    }
                } else {
                    mLog.w("No in-flight alarm for " + pi + " " + intent);
                }
                mTriggeredUids.remove(new Integer(uid));
                if(mBlockedUids.contains(new Integer(uid))) {
                    mBlockedUids.remove(new Integer(uid));
                } else {
                    if(mBroadcastRefCount > 0){
                        mBroadcastRefCount--;
                        if (mBroadcastRefCount == 0) {
                            mWakeLock.release();
                            if (mInFlight.size() > 0) {
                                mLog.w("Finished all broadcasts with " + mInFlight.size()
                                    + " remaining inflights");
                                for (int i=0; i<mInFlight.size(); i++) {
                                    mLog.w("  Remaining #" + i + ": " + mInFlight.get(i));
                                }
                                mInFlight.clear();
                            }
                        }
                    } else {
                        if(localLOGV) {
                            Slog.e(TAG,"Trying to decrement mBroadcastRefCnt past zero");
                        }
                    }
                }
                if (mBroadcastRefCount != 0) {
                    // the next of our alarms is now in flight.  reattribute the wakelock.
                    if (mInFlight.size() > 0) {
                        InFlight inFlight = mInFlight.get(0);
                        setWakelockWorkSource(inFlight.mPendingIntent, inFlight.mWorkSource);
                    } else {
                        // should never happen
                        try {
                        mLog.w("Alarm wakelock still held but sent queue empty");
                        mWakeLock.setWorkSource(null);
                        } catch (IllegalArgumentException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    /*[lenovo_fw,jiangzc1,lenovo_alarm], "Refer to lenovo_fw.txt" , modify*/
    // Lenovo modifications begin

    static final ArrayList<String> CANDIDATE_PACKAGES = new ArrayList<String>();
    static {
        CANDIDATE_PACKAGES.add("com.lenovomobile.deskclock");
        CANDIDATE_PACKAGES.add("com.lenovo.deskclock");
    }
    private static final String RTC_ALARM_TIME_PROPERTY = "persist.sys.rtc_alarm_time";
    private static final String RTC_START_TIME_PROPERTY = "persist.sys.rtc_start_time";
    /*[lenovo_fw,jiangzc1,lenovo_alarm], end*/

    /*[lenovo_fw,wangchao9,alarm_policy], "Refer to lenovo_fw.txt" , modify*/

    /* Begin,Lenovo-sw piyy1 add 2014-05-20, delete for resolve application force close under mulitiple user */
    /*static final ArrayList<String> SYSTEM_WHITELIST = new ArrayList<String>();
    // Top 10 calendars and alarms goes here.
    static {
        SYSTEM_WHITELIST.add("android");
        SYSTEM_WHITELIST.add("com.android.cts");
        SYSTEM_WHITELIST.add("com.android.cts.stub");
        SYSTEM_WHITELIST.add("com.zdworks.android.zdclock");
        SYSTEM_WHITELIST.add("com.dianxinos.clock");
        SYSTEM_WHITELIST.add("com.lanhuayuan.worldclock");
        SYSTEM_WHITELIST.add("cn.etouch.ecalendar");
        SYSTEM_WHITELIST.add("com.youloft.calendar");
        SYSTEM_WHITELIST.add("com.when.android.calendar365");
        SYSTEM_WHITELIST.add("com.calendar.UI");
        SYSTEM_WHITELIST.add("cn.etouch.ecalendar.pad");
        SYSTEM_WHITELIST.add("com.google.android.syncadapters.calendar");
        SYSTEM_WHITELIST.add("net.qihoo.launcher.widget.clockweather");
    }

    private final Uri WHITELIST_URL = Uri
            .parse("content://com.android.settings.applications.SpProvider/whitelist");
    private List<String> mWhiteList = new ArrayList<String>();
    */
    /* End,Lenovo-sw piyy1 add 2014-05-20, delete for resolve application force close under mulitiple user */

   /*Begin,Lenovo-sw piyy1 add 2014-05-20, add for resolve application force close under mulitiple user */
    static final ArrayList<String> SYSTEM_BLACKLIST = new ArrayList<String>();
    static {
        SYSTEM_BLACKLIST.add("com.lenovo.lsf.device");
        SYSTEM_BLACKLIST.add("com.lenovo.videotalk.phone");
        SYSTEM_BLACKLIST.add("com.lenovo.vctl.weaver.alert");
        SYSTEM_BLACKLIST.add("com.sina.weibo");
        SYSTEM_BLACKLIST.add("com.tencent.mm");
        SYSTEM_BLACKLIST.add("com.tencent.minihd.qq");
        SYSTEM_BLACKLIST.add("com.tencent.hd.qq");
        SYSTEM_BLACKLIST.add("com.tencent.android.pad");
        SYSTEM_BLACKLIST.add("com.tencent.qq");
    }
    /*End,Lenovo-sw piyy1 add 2014-05-20, add for resolve application force close under mulitiple user */

    private boolean pkgInWhitelist(String pkg) {

        synchronized (mLock) {
            /* Begin,Lenovo-sw piyy1 add 2014-05-20, modify for resolve application force close under mulitiple user */
            /* Original
            if (SYSTEM_WHITELIST.contains(pkg)) {
                return true;
            }

            PackageManager pm = mContext.getPackageManager();
            try {
                if ((pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mWhiteList.clear();
            Cursor cursor = mContext.getContentResolver().query(WHITELIST_URL,
                    null, null, null, null);
            if (cursor == null) {
                Slog.d(TAG, "can't get the whitelist cursor");
                return false;
            }
            if (cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    String name = cursor.getString(cursor
                            .getColumnIndex("name"));
                    mWhiteList.add(name);
                    cursor.moveToNext();
                }
            }
            cursor.close();
            return mWhiteList.contains(pkg);
            */

            if (SYSTEM_BLACKLIST.contains(pkg)) {
                return false;
            }
            return SYSTEM_BLACKLIST.contains(pkg);
            /* End,Lenovo-sw piyy1 add 2014-05-20, delete for resolve application force close under mulitiple user */
        }
    }
    /*[lenovo_fw,wangchao9,alarm_policy], end*/
}