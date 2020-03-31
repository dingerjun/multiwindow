package com.android.server;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.IStartShutdownManager;
import android.app.KeyguardManager;
import android.app.StartShutdownManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.widget.ImageView;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.server.power.ShutdownThread;

public class StartShutdownService extends IStartShutdownManager.Stub {
    private static final String TAG = "StartShutdownService";
    private final Context mContext;

    private KeyguardManager mKeyguardManager;
    private KeyguardManager.KeyguardLock mKeyguardLock;
    private PowerManager.WakeLock mAlarmScreenOnWakeLock = null;

    private StartShutdownPolicy mPolicy;

    private AlertDialog mDataDialog;
    private AlertDialog mNoSimDialog;
    private boolean mSystemBusy;
    private boolean mSimReady;
    private boolean mLauncherReady;
    private boolean mBootCompleted;
    private boolean mSimReadyOnce;
    private boolean mDataDialogShown;
    private State mState;
    private final int mFactoryTest;
    private ServiceState mServiceState;
    private int mPhoneState = TelephonyManager.CALL_STATE_IDLE;
    private boolean mDataDialogShowOnEndCall;

    private static final int MESSAGE_ENABLE_AUTO_START = 1;
    private static final int MESSAGE_DISABLE_AUTO_START = 2;
    private static final int MESSAGE_ENABLE_AUTO_SHUTDOWN = 3;
    private static final int MESSAGE_DISABLE_AUTO_SHUTDOWN = 4;
    private static final int MESSAGE_SHUTDOWN = 5;
    private static final int MESSAGE_HIDE_SYSTEM_WINDOW = 8;
    private static final int MESSAGE_LAUNCHER_READY = 9;
    private static final int MESSAGE_REBOOT = 10;

    private static final int DELAY_LAUNCHER_READY_STEP = 1500;
    private static final int TIMEOUT_LAUNCHER_READY = 15000;

    private static final int MASK_BOOT_KEYPAD = 0x01;
    private static final int MASK_BOOT_RTC = 0x02;
    private static final int MASK_BOOT_CABLE = 0x04;
    private static final int MASK_BOOT_SMPL = 0x08;
    private static final int MASK_BOOT_WDOG = 0x10;
    private static final int MASK_BOOT_USBCHARGE = 0x20;
    private static final int MASK_BOOT_WALLCHARGE = 0x40;

    static public final String INTENT_VALUE_ICC_ABSENT = "ABSENT";
    static public final String INTENT_VALUE_ICC_LOCKED = "LOCKED";
    static public final String INTENT_VALUE_ICC_READY = "READY";
    static public final String INTENT_KEY_ICC_STATE = "ss";
    static public final String INTENT_KEY_LOCKED_REASON = "reason";
    static public final String INTENT_VALUE_LOCKED_ON_PIN = "PIN";
    static public final String INTENT_VALUE_LOCKED_ON_PUK = "PUK";
    static public final String INTENT_VALUE_LOCKED_NETWORK = "NETWORK";
    static public final String INTENT_VALUE_ICC_REFRESH = "REFRESH";
    static public final String INTENT_VALUE_LOCKED_RUIM_HRPD = "RUIM HRPD";
    static public final String INTENT_VALUE_LOCKED_RUIM_CORPORATE = "RUIM CORPORATE";
    static public final String INTENT_VALUE_LOCKED_RUIM_SERVICE_PROVIDER = "RUIM SERVICE PROVIDER";
    static public final String INTENT_VALUE_LOCKED_RUIM_RUIM = "RUIM RUIM";
    static public final String INTENT_VALUE_ICC_CARD_IO_ERROR = "CARD_IO_ERROR";
    static public final String INTENT_VALUE_LOCKED_NETWORK_SUBSET = "SIM NETWORK SUBSET";
    static public final String INTENT_VALUE_LOCKED_CORPORATE = "SIM CORPORATE";
    static public final String INTENT_VALUE_LOCKED_SERVICE_PROVIDER = "SIM SERVICE PROVIDER";
    static public final String INTENT_VALUE_LOCKED_SIM = "SIM SIM";
    static public final String INTENT_VALUE_LOCKED_RUIM_NETWORK1 = "RUIM NETWORK1";
    static public final String INTENT_VALUE_LOCKED_RUIM_NETWORK2 = "RUIM NETWORK2";

    static final String FEATURE_ENABLE_NET = "enalbeNET";
    static final String FEATURE_ENABLE_WAP = "enableWAP";
    static final String APN_TYPE_DEFAULT = "default";
    static final String APN_TYPE_WAP = "wap";

    public static final String EPC_CURRENT_ATTEMPTS = "epc_current_attempts_remaining";

    public static final String STK_IDLE_SCREEN_ACTION = "android.intent.action.stk.idle_screen";

    public static final String CHECK_SCREEN_IDLE_ACTION = "android.intent.action.stk.check_screen_idle";

    public static final int BATTERY_PLUGGED_DOCKING = 3;
    public static final int BOOTMODE_USER = 0;
    public static final int BOOTMODE_CHARGEIN = 1;
    public static final int BOOTMODE_RESET = 2;
    public static final int BOOTMODE_RTC_ALARM = 3;
    public static final int BOOTMODE_RTC_STARTUP = 4;

    public static final String BOOT_ANIMA_PROPERTY = "sys.power.startup_mode";
    public static final String BOOT_ANIMA_ALARM = "alarm";
    public static final String BOOT_ANIMA_CHARGE = "charge";
    public static final String BOOT_ANIMA_NORMAL = "normal";
    // This is set by ShutdownThread
    public static final String BOOT_ANIMA_POWER_OFF = "poweroff";

    static final String BOOT_SILENTLY_PROPERTY = "persist.sys.boot_silently";
    static final String SHUTDOWN_SILENTLY_PROPERTY = "persist.sys.shutdown_silently";
    static final String RTC_START_TIME_PROPERTY = "persist.sys.rtc_start_time";
    static final String RTC_ALARM_TIME_PROPERTY = "persist.sys.rtc_alarm_time";
    static final String SHUTDOWN_AND_CHARGING_PROPERTY = "persist.sys.shutdown_charge";
    
    public interface ServiceCallback {
        public void shutdownAction();
    }

    enum State {
        Notify, Startup, Shutdown, Idle
    }

    private static int sBootMode;
    private static int sBootEnum;
    private static Object sLock = new Object();

    private final ServiceCallback mServiceCallback = new ServiceCallback() {
        public void shutdownAction() {
            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_SHUTDOWN, 0, 0));
        }
    };

    public static StartShutdownService main(Context context, int factoryTest) {
        SSThread thr = new SSThread(context, factoryTest);
        thr.start();

        synchronized (thr) {
            while (thr.mService == null) {
                try {
                    thr.wait();
                } catch (InterruptedException e) {
                }
            }
        }

        return thr.mService;
    }

    static class SSThread extends Thread {
        StartShutdownService mService;
        private final Context mContext;
        private final int mFactoryTest;

        public SSThread(Context context, int factoryTest) {
            super("StartShutdownService");
            mContext = context;
            mFactoryTest = factoryTest;
        }

        public void run() {
            Looper.prepare();
            StartShutdownService s = new StartShutdownService(mContext,
                    mFactoryTest);
            android.os.Process
                    .setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
            synchronized (this) {
                mService = s;
                notifyAll();
            }
            Looper.loop();
        }
    }

    private synchronized void disableKeyguard() {
        if (mKeyguardLock == null) {
            mKeyguardManager = (KeyguardManager) mContext
                    .getSystemService(Context.KEYGUARD_SERVICE);
            mKeyguardLock = mKeyguardManager.newKeyguardLock(TAG);
            mKeyguardLock.disableKeyguard();
        }
    }

    private synchronized void enableKeyguard() {
        if (mKeyguardLock != null) {
            mKeyguardLock.reenableKeyguard();
            mKeyguardLock = null;
        }
    }

    public StartShutdownService(Context context, int factoryTest) {
        mContext = context;
        mFactoryTest = factoryTest;

        if (mFactoryTest != SystemServer.FACTORY_TEST_OFF)
            return;

        mPolicy = new StartShutdownPolicy(mContext, mServiceCallback);

        boolean reducedBoot = (sBootMode == StartShutdownManager.BOOTMODE_RTC_ALARM);
        mPolicy.init(reducedBoot);
     
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);

        ((TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE)).listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_CALL_STATE);

    }

    public void systemReady() {
        loadSettings();
        if (mFactoryTest != SystemServer.FACTORY_TEST_OFF) {

            startFullSystem();
            return;
        }
        Slog.d(TAG, "******StartShutdownService --- systemReady:mBootMode="
                + sBootMode + "******");
        switch (sBootMode) {
        case StartShutdownManager.BOOTMODE_CHARGEIN:
            Slog.d("R2", "Disable keyguard for RTC Alarm!");
            disableKeyguard();
            break;
        case StartShutdownManager.BOOTMODE_USER:
        case StartShutdownManager.BOOTMODE_RESET:
        case StartShutdownManager.BOOTMODE_RTC_STARTUP:
            startFullSystem();
            break;
        }
    }

    private void startFullSystem() {
        Slog.i("R2", "Touch Startfull.");
        SystemProperties.set("sys.power.startup_ready", "1");
        mState = State.Idle;

        int timeout = mLauncherReady ? 0 : TIMEOUT_LAUNCHER_READY;
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(MESSAGE_LAUNCHER_READY), timeout);

        if (mFactoryTest != SystemServer.FACTORY_TEST_OFF) {
            return;
        }

        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            if (am != null) {
                Configuration config = am.getConfiguration();
                if (config != null) {
                    am.updateConfiguration(config);
                }
            }
        } catch (Exception e) {
        }

        if (sBootMode == StartShutdownManager.BOOTMODE_CHARGEIN) {
            SystemProperties.set("sys.power.disable_radio", "0");
            Slog.i("R2", "Touch Start Full system, release locks now.");
            enableKeyguard();
            try {
                final ITelephony sPhone = ITelephony.Stub
                        .asInterface(ServiceManager.checkService("phone"));
                if (sPhone != null && !sPhone.isRadioOn()) {
                    sPhone.setRadio(true);
                }
            } catch (RemoteException ex) {
                Slog.e(TAG, "RemoteException during radio on", ex);
            }
        }
        mPolicy.onBootCompleted();
    }

    public void launcherReady() {
        mLauncherReady = true;
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(MESSAGE_LAUNCHER_READY),
                DELAY_LAUNCHER_READY_STEP);
    }

    public int getBootMode() {
        return sBootMode;
    }

    public static int getStaticBootMode() {
        return sBootMode;
    }

    public void shutdown(boolean needConfirm) {
        int confirm = needConfirm ? 1 : 0;
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_SHUTDOWN, confirm,
                0));
    }

    public void reboot() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_REBOOT));
    }

    public void setSystemBusy(boolean busy) {
        synchronized (sLock) {
            mSystemBusy = busy;
        }
    }

    public void poweroffAlarmAlertPrepare() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_HIDE_SYSTEM_WINDOW));
    }

    public void poweroffAlarmAlertFinish() {
        if (mState == State.Notify) {
            mServiceCallback.shutdownAction();
        }
    }

    public boolean enableAutoStart(int hour, int minute) {
        if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
            mHandler.sendMessage(mHandler.obtainMessage(
                    MESSAGE_ENABLE_AUTO_START, hour, minute));
            return true;
        } else {
            return false;
        }
    }

    public void disableAutoStart() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_DISABLE_AUTO_START));
    }

    public boolean isAutoStartEnable() {
        synchronized (sLock) {
            boolean result = false;
            if (mPolicy != null) {
                result = mPolicy.isAutoStartTimeSet();
            }
            return result;
        }
    }

    public boolean enableAutoShutdown(int hour, int minute) {
        if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
            mHandler.sendMessage(mHandler.obtainMessage(
                    MESSAGE_ENABLE_AUTO_SHUTDOWN, hour, minute));
            return true;
        } else {
            return false;
        }
    }

    public void disableAutoShutdown() {
        mHandler.sendMessage(mHandler
                .obtainMessage(MESSAGE_DISABLE_AUTO_SHUTDOWN));
    }

    public boolean isAutoShutdownEnable() {
        synchronized (sLock) {
            boolean result = false;
            if (mPolicy != null) {
                mPolicy.isAutoShutdownTimeSet();
            }
            return result;
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_ENABLE_AUTO_START:
                if (mPolicy != null) {
                    mPolicy.enableAutoStart(msg.arg1, msg.arg2);
                }
                break;

            case MESSAGE_DISABLE_AUTO_START:
                if (mPolicy != null) {
                    mPolicy.disableAutoStart();
                }
                break;

            case MESSAGE_ENABLE_AUTO_SHUTDOWN:
                if (mPolicy != null) {
                    mPolicy.enableAutoShutdown(msg.arg1, msg.arg2);
                }
                break;

            case MESSAGE_DISABLE_AUTO_SHUTDOWN:
                if (mPolicy != null) {
                    mPolicy.disableAutoShutdown();
                }
                break;

            case MESSAGE_SHUTDOWN:
                Slog.i(TAG, "shutdown device..");
                if (mState == State.Idle) {
                    boolean confirm = mSystemBusy ? true : (msg.arg1 != 0);
                    ShutdownThread.shutdown(mContext, confirm);
                    mState = State.Shutdown;
                } else if (mState == State.Notify) {
                    ShutdownThread.shutdown(mContext, false);
                    mState = State.Shutdown;
                }
                break;

            case MESSAGE_LAUNCHER_READY:
                handleLauncherReady();
                break;
            case MESSAGE_REBOOT: {
                ShutdownThread.reboot(mContext, "poweroff_alarm_reboot", false);
            }
            default:
                break;
            }
        }
    };

    private void handleLauncherReady() {
        if (mBootCompleted)
            return;

        if (mState != State.Idle)
            return;

        Slog.d(TAG,
                "******StartShutdownService --- MESSAGE_LAUNCHER_READY******");
        mBootCompleted = true;
    }

    private void loadSettings() {
        if (getBootMode() == BOOTMODE_RTC_ALARM) {
            Slog.d(TAG,
                    "******StartShutdownService ---do not LoadSettings in RTC_ALARM mode******");
            return;
        }
        Slog.d(TAG, "******StartShutdownService --- LoadSettings******");
        if (isAirplaneModeOn()) {
            Slog.d(TAG, "******StartShutdownService --- AirplaneModeOn******");
            AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(com.android.internal.R.string.startup_confirm_airplane_turn_off)
                    .setPositiveButton(com.android.internal.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    setAirplaneModeOff();
                                }
                            }).setNegativeButton(com.android.internal.R.string.no, null).create();

            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    showDataConnectionDialog();
                }
            });

            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    showDataConnectionDialog();
                }
            });

            dialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);

            dialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

            dialog.show();
        } else {
            showDataConnectionDialog();
        }
    }

    private void showDataConnectionDialog() {
        int on = Settings.System.getInt(mContext.getContentResolver(),
                "mobile_data_enable", 0);
        int firstBoot = Settings.System.getInt(mContext.getContentResolver(),
                "first_boot_on", 0);
        Slog.d(TAG, "******showDataConnectionDialog --- mSimReady:" + mSimReady
                + ",on:" + on + ",mDataDialog:" + mDataDialog + "******");
        if (!mDataDialogShown && mSimReady && !isAirplaneModeOn()
                && hasService() && on == 0 && mDataDialog == null
                && firstBoot == 0) {
            // LenovoAdapter.startSystemInfoLicenseActivity(mContext);
            Settings.System.putInt(mContext.getContentResolver(),
                    "first_boot_on", 1);
        }
    }

	private boolean isAirplaneModeOn() {
		return Settings.System.getInt(mContext.getContentResolver(),
				Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
	}

	private void setAirplaneModeOff() {
		Settings.Global.putInt(mContext.getContentResolver(),
				Settings.Global.AIRPLANE_MODE_ON, 0);

        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", false);
        mContext.sendBroadcast(intent);
    }

    public static void powerSaveWhileCharging() {
    }

    static final String AC_CHARGER = "ac_charger";
    static final String USB_CABLE = "usb_cable";
    static final String RTC = "rtc_alarm";

    public static void init() {
        String kBootMode = SystemProperties.get("ro.boot.mode", "unknown");
        String sysBootMode = SystemProperties.get("sys.boot.mode", "unknown");
        if (!sysBootMode.equals("unknown")) {
            kBootMode = sysBootMode;
        }

        sBootMode = BOOTMODE_USER;

	if (kBootMode.equals(AC_CHARGER) || kBootMode.equals(USB_CABLE)) {
            sBootMode = BOOTMODE_CHARGEIN;
        }
        if (kBootMode.equals(RTC)) {
            //sBootMode = BOOTMODE_RTC_STARTUP;
        }
    }

    private static void setBootAnim() {
        String bootAnim = BOOT_ANIMA_NORMAL;
        switch (sBootMode) {
        case BOOTMODE_RTC_ALARM:
            bootAnim = BOOT_ANIMA_ALARM;
            break;
        case BOOTMODE_CHARGEIN:
            bootAnim = BOOT_ANIMA_CHARGE;
            break;
        }
        SystemProperties.set(BOOT_ANIMA_PROPERTY, bootAnim);
    }

    private boolean isTeleProduct(){
    	String operator=SystemProperties.get("ro.product.name","kiton");
    	Slog.d(TAG,"product:"+operator);
    	return operator.equals("kitone");
    }
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String stateExtra = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Slog.d(TAG,
                        "icc stateExtra:" + stateExtra);
                boolean old = mSimReady;
                String strIccCardIOState = getConstValueByModuleName("IccCard_INTENT_VALUE_ICC_CARD_IO_ERROR");
                boolean isIccCardIOError = strIccCardIOState.equals(stateExtra) ? true
                        : false;
                mSimReady = !(IccCardConstants.INTENT_VALUE_ICC_ABSENT
                        .equals(stateExtra) || isIccCardIOError || IccCardConstants.INTENT_VALUE_ICC_LOCKED
                        .equals(stateExtra));
                if (!old && mSimReady) {
                    showDataConnectionDialog();
                }

                if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                    mSimReadyOnce = true;
                }

                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                     String strSKU = SystemProperties.get("ro.lenovo.tablet");
                	int titleResourceId=
                			isTeleProduct() ? com.android.internal.R.string.poweron_nouim : com.android.internal.R.string.poweron_nosim;
                    if ((!(sBootMode == StartShutdownManager.BOOTMODE_RTC_ALARM || sBootMode == StartShutdownManager.BOOTMODE_CHARGEIN))
                            && !isAirplaneModeOn()
                            && !mSimReadyOnce
                            && mNoSimDialog == null
                            && ("3gcall".equals(strSKU))) {
                        mNoSimDialog = new AlertDialog.Builder(mContext)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setTitle(
                                		titleResourceId)
                                .setMessage(
                                        com.android.internal.R.string.poweron_emergencycallonly)
                                .setPositiveButton(com.android.internal.R.string.yes, null).create();
                        mNoSimDialog.getWindow().setType(
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                        mNoSimDialog.getWindow().addFlags(
                                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        mNoSimDialog.show();
                        mNoSimDialog.setOnDismissListener(mNoSimListener);
                    }
                } else if (mNoSimDialog != null) {
                    mNoSimDialog.dismiss();
                }
            }
        }

    };

    private DialogInterface.OnDismissListener mNoSimListener = new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface dialog) {
            mNoSimDialog = null;
        }
    };

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        public void onServiceStateChanged(ServiceState state) {
            boolean old = hasService();
            mServiceState = state;
            if (!old && hasService()) {
                showDataConnectionDialog();
            }
        }

        public void onCallStateChanged(int state, String incomingNumber) {
            mPhoneState = state;
            if (mPhoneState == TelephonyManager.CALL_STATE_IDLE) {
                if (mDataDialogShowOnEndCall) {
                    showDataConnectionDialog();
                    mDataDialogShowOnEndCall = false;
                }
            } else {
                if (mDataDialog != null) {
                    mDataDialog.dismiss();
                    mDataDialogShowOnEndCall = true;
                    mDataDialog = null;
                }
            }
        }
    };

    public static String getConstValueByModuleName(String moduleName) {
        if (moduleName == null)
            return null;
        if (moduleName.equals("IccCard_INTENT_VALUE_ICC_CARD_IO_ERROR"))
            return INTENT_VALUE_ICC_CARD_IO_ERROR;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_NETWORK_SUBSET"))
            return INTENT_VALUE_LOCKED_NETWORK_SUBSET;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_CORPORATE"))
            return INTENT_VALUE_LOCKED_CORPORATE;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_SERVICE_PROVIDER"))
            return INTENT_VALUE_LOCKED_SERVICE_PROVIDER;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_SIM"))
            return INTENT_VALUE_LOCKED_SIM;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_RUIM_NETWORK1"))
            return INTENT_VALUE_LOCKED_RUIM_NETWORK1;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_RUIM_NETWORK2"))
            return INTENT_VALUE_LOCKED_RUIM_NETWORK2;

        if (moduleName.equals("IccCard_INTENT_VALUE_ICC_REFRESH"))
            return INTENT_VALUE_ICC_REFRESH;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_RUIM_HRPD"))
            return INTENT_VALUE_LOCKED_RUIM_HRPD;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_RUIM_CORPORATE"))
            return INTENT_VALUE_LOCKED_RUIM_CORPORATE;
        if (moduleName
                .equals("IccCard_INTENT_VALUE_LOCKED_RUIM_SERVICE_PROVIDER"))
            return INTENT_VALUE_LOCKED_RUIM_SERVICE_PROVIDER;
        if (moduleName.equals("IccCard_INTENT_VALUE_LOCKED_RUIM_RUIM"))
            return INTENT_VALUE_LOCKED_RUIM_RUIM;

        if (moduleName.equals("Phone_APN_TYPE_DEFAULT"))
            return APN_TYPE_DEFAULT;
        if (moduleName.equals("Phone_APN_TYPE_WAP"))
            return APN_TYPE_WAP;
        if (moduleName.equals("Phone_FEATURE_ENABLE_NET"))
            return FEATURE_ENABLE_NET;
        if (moduleName.equals("Phone_FEATURE_ENABLE_WAP"))
            return FEATURE_ENABLE_WAP;
        if (moduleName.equals("BatteryManager_BATTERY_PLUGGED_DOCKING"))
            return String.valueOf(BATTERY_PLUGGED_DOCKING);
        if (moduleName.equals("AppInterface_STK_IDLE_SCREEN_ACTION"))
            return STK_IDLE_SCREEN_ACTION;
        if (moduleName.equals("AppInterface_CHECK_SCREEN_IDLE_ACTION"))
            return CHECK_SCREEN_IDLE_ACTION;
        if (moduleName.equals("Settings_EPC_CURRENT_ATTEMPTS"))
            return EPC_CURRENT_ATTEMPTS;

        return null;
    }

    private boolean hasService() {
        if (mServiceState != null) {
            switch (mServiceState.getState()) {
            case ServiceState.STATE_OUT_OF_SERVICE:
            case ServiceState.STATE_POWER_OFF:
                return false;
            default:
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean isBootCompleted() {
        return (SystemProperties.get("sys.power.startup_ready").equals("1"));
    }

    public void setSilentBoot(boolean silent) {
		SystemProperties.set(BOOT_SILENTLY_PROPERTY, Boolean.toString(silent));
    }

    public boolean isSilentBoot() {
        return SystemProperties.getBoolean(BOOT_SILENTLY_PROPERTY, false);
    }

    public boolean isSilentShutdown() {
        return SystemProperties.getBoolean(SHUTDOWN_SILENTLY_PROPERTY, false);
    }

    public void setSilentShutdown(boolean silent) {
		SystemProperties.set(SHUTDOWN_SILENTLY_PROPERTY, Boolean.toString(silent));
    }

}
