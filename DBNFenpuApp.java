package app;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVInstallation;
import com.avos.avoscloud.AVMixpushManager;
import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.SaveCallback;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.pangu.fenpu.dbnfenpu_android.LeanCloudActivity;
//import com.squareup.leakcanary.LeakCanary;
import com.tencent.bugly.crashreport.CrashReport;
import com.umeng.analytics.MobclickAgent;

import org.xutils.common.Callback;
import org.xutils.http.RequestParams;
import org.xutils.x;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chat.MyMessageReceiveListener;
import gsonbean.TokenBean;
import io.rong.imkit.RongContext;
import io.rong.imkit.RongIM;
import io.rong.imkit.widget.provider.CameraInputProvider;
import io.rong.imkit.widget.provider.ImageInputProvider;
import io.rong.imkit.widget.provider.InputProvider;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.push.RongPushClient;
import io.rong.push.common.RongException;
import utils.Constant;
import httprequest.HttpUtils;
import utils.LocalUtils;
import utils.SharePreferenceUtil;
import httprequest.StringCallback;
import utils.UploadSqlteHelper;
import utils.UserDBUtil;
import chat.ProductMessage;
import chat.UrlMessagePovider;
import chat.UrlProvider;
import chat.MyUserProvier;


/**
 * Created by wangpeng on 16/6/24.
 */
public class DBNFenpuApp extends Application {

    public static DBNFenpuApp appcontext;
    public static int sdkVesion;
    public static ExecutorService cachedThreadPool;
    public static DisplayImageOptions defaultOptions;
//    private static List<Activity> activityList = new ArrayList();

//    private RefWatcher refWatcher;
//
//    public static RefWatcher getRefWatcher(Context context) {
//        DBNFenpuApp application = (DBNFenpuApp) context.getApplicationContext();
//        return application.refWatcher;
//    }

    @Override
    public void onCreate() {
        super.onCreate();
        appcontext = this;
        sdkVesion = Build.VERSION.SDK_INT;
//        if(BuildConfig.DEBUG){
//        refWatcher=LeakCanary.install(appcontext);
//        }
        x.Ext.init(appcontext);


//        LocalImageUtils.initImageUtils(appcontext);
        //初始化美洽
        LocalUtils.initMeiQia(appcontext);
        //初始化imageloader
        LocalUtils.initImageloader(appcontext);
        defaultOptions=LocalUtils.initDisplay();
        //初始化imageloader默认显示图片设置
        CrashReport.initCrashReport(appcontext, "900051024", false);


        initSpUtils();
        //初始化融云
        initRongIM();
        //初始化推送
        initPush();
        //初始化友盟
        initUmeng();
        //线程池
        initThreadPool();

        //初始化数据库
        UploadSqlteHelper helper=new UploadSqlteHelper(this);


    }

    private void initRongIM() {

        RongPushClient.registerMiPush(appcontext, Constant.Mi_AppId, Constant.Mi_AppKey);
        RongPushClient.registerHWPush(appcontext);
        try {
            RongPushClient.checkManifest(appcontext);
        } catch (RongException e) {
            e.printStackTrace();
        }
        RongIM.init(appcontext);
        RongPushClient.init(appcontext, Constant.RongIm_AppKey);
        //扩展功能自定义
        InputProvider.ExtendProvider[] provider = {
                new UrlProvider(RongContext.getInstance()),
                new ImageInputProvider(RongContext.getInstance()),//图片
                new CameraInputProvider(RongContext.getInstance()),//相机
//                new LocationInputProvider(RongContext.getInstance()),//地理位置
//                new VoIPInputProvider(RongContext.getInstance()),// 语音通话
//                new ContactsProvider(RongContext.getInstance())//自定义通讯录
        };
        RongIM.resetInputExtensionProvider(Conversation.ConversationType.PRIVATE, provider);
        RongIM.registerMessageType(ProductMessage.class);
        RongIM.getInstance().registerMessageTemplate(new UrlMessagePovider());
        RongIM.setUserInfoProvider(new MyUserProvier(), true);
        RongIM.setOnReceiveMessageListener(new MyMessageReceiveListener());


        //如果已登陆,用本地存储信息请求信息,如果未登陆不初始化,登录完成之后请求
        UserDBUtil util = new UserDBUtil(DBNFenpuApp.appcontext);

        if (SharePreferenceUtil.getPrefBoolean(Constant.IsLgoin, false)) {
            if (util.isExist(SharePreferenceUtil.getPrefLong(Constant.User_Id, 0L) + "")) {
                ContentValues values = new ContentValues();
                values.put(Constant.User_Name, SharePreferenceUtil.getPrefString(Constant.User_Name, ""));
                values.put(Constant.User_Avatar, SharePreferenceUtil.getPrefString(Constant.User_Avatar, ""));
                util.update(values, Constant.User_Id + "=?", new String[]{SharePreferenceUtil.getPrefLong(Constant.User_Id, 0L) + ""});
                values = null;

            } else {
                ContentValues values = new ContentValues();
                values.put(Constant.User_Id, SharePreferenceUtil.getPrefLong(Constant.User_Id, 0L) + "");
                values.put(Constant.User_Name, SharePreferenceUtil.getPrefString(Constant.User_Name, ""));
                values.put(Constant.User_Avatar, SharePreferenceUtil.getPrefString(Constant.User_Avatar, ""));
                util.insert(values);
                values = null;
            }

            RequestParams params;
            if (SharePreferenceUtil.getPrefBoolean(Constant.IsFirstTime, false)) {
                params = new RequestParams(Constant.Interface + Constant.GetUserToken);
                SharePreferenceUtil.setPrefBoolean(Constant.IsFirstTime, false);
            } else {
                params = new RequestParams(Constant.Interface + Constant.RefreshUserToken);
            }
            params.addBodyParameter(Constant.User_Name, SharePreferenceUtil.getPrefString(Constant.User_Name, ""));
            params.addBodyParameter(Constant.User_Avatar, SharePreferenceUtil.getPrefString(Constant.User_Avatar, ""));
            params.addBodyParameter(Constant.Device, Constant.Device_Android);
            if (isApkDebugable(DBNFenpuApp.appcontext)) {
                params.addBodyParameter("type", "0");
            } else {
                params.addBodyParameter("type", "1");
            }
            params.addBodyParameter(Constant.AppName, Constant.AppName_DBN);
            params.addBodyParameter(Constant.UDID, SharePreferenceUtil.getPrefString(Constant.UDID, ""));
            params.addBodyParameter(Constant.SessionKey, SharePreferenceUtil.getPrefString(Constant.SessionKey, ""));
            HttpUtils.postRequset(params, new Callback.CommonCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    TokenBean tokenBean = JSON.parseObject(result, TokenBean.class);
                    if (tokenBean.getError() == 0) {
                        if (!TextUtils.isEmpty(tokenBean.getToken())) {
                            SharePreferenceUtil.setPrefString(Constant.Token, tokenBean.getToken());
                            RongIM.connect(tokenBean.getToken(), new RongIMClient.ConnectCallback() {
                                @Override
                                public void onTokenIncorrect() {
                                    Log.e("RongIm", "onTokenIncorrect: ");
                                }

                                @Override
                                public void onSuccess(String s) {
                                    Log.e("RongIm", "onSuccess: ");
                                }

                                @Override
                                public void onError(RongIMClient.ErrorCode errorCode) {
                                    Log.e("RongIm", "onError: ");
                                }
                            });
                        }
                    }
                }

                @Override
                public void onError(Throwable ex, boolean isOnCallback) {

                }

                @Override
                public void onCancelled(CancelledException cex) {
                }

                @Override
                public void onFinished() {
                }
            });
//  RongIM.connect()
        }
    }


    private void initThreadPool() {
        cachedThreadPool = Executors.newCachedThreadPool();
    }


    private void initUmeng() {
        MobclickAgent.setScenarioType(DBNFenpuApp.appcontext, MobclickAgent.EScenarioType.E_UM_NORMAL);
        MobclickAgent.setSessionContinueMillis(6000);
    }


    private void initPush() {
        AVOSCloud.initialize(DBNFenpuApp.appcontext, Constant.LeanCloud_ID, Constant.LeanCloud_Key);
        // 启用崩溃错误统计

        AVMixpushManager.registerXiaomiPush(DBNFenpuApp.appcontext, Constant.Mi_AppId, Constant.Mi_AppSecret, "pangu");
        AVMixpushManager.registerHuaweiPush(DBNFenpuApp.appcontext, "pangu");
// AVAnalytics.enableCrashReport(this.getApplicationContext(), true);
        AVOSCloud.setLastModifyEnabled(true);
        AVOSCloud.setDebugLogEnabled(true);

        AVInstallation.getCurrentInstallation().saveInBackground(new SaveCallback() {
            @Override
            public void done(AVException e) {
                String installationId = AVInstallation.getCurrentInstallation().getInstallationId();
                SharePreferenceUtil.setPrefString(Constant.Device_Token, installationId);
                if (SharePreferenceUtil.getPrefBoolean(Constant.IsLgoin, false)) {
                    RequestParams params = new RequestParams(Constant.Interface + Constant.UpdatDeviceToken);
                    params.addBodyParameter(Constant.Device_Token, installationId);
                    params.addBodyParameter(Constant.Device_Type, Constant.Device_Android);
                    params.addBodyParameter(Constant.SessionKey, SharePreferenceUtil.getPrefString(Constant.SessionKey, ""));
                    params.addBodyParameter(Constant.Device, Constant.Device_Android);
                    params.addBodyParameter(Constant.AppName, Constant.AppName_DBN);
                    if (!TextUtils.isEmpty(SharePreferenceUtil.getPrefString(Constant.UDID, "")))
                        params.addBodyParameter(Constant.UDID, SharePreferenceUtil.getPrefString(Constant.UDID, ""));
                    HttpUtils.postRequset(params, new StringCallback());
                    SharePreferenceUtil.setPrefBoolean(Constant.Is_Refresh_Token, true);
                    Log.e("tag", "avos: 链接成功");
                } else {
                    SharePreferenceUtil.setPrefBoolean(Constant.Is_Refresh_Token, false);
                    Log.e("tag", "avos: 链接失败");
                }
            }


        });

        com.avos.avoscloud.PushService.setDefaultPushCallback(this, LeanCloudActivity.class);
        AVObject testObject = new AVObject("TestObject");
        testObject.put("foo", "bar");
        testObject.saveInBackground();
    }

    private void initSpUtils() {
        utils.SharePreferenceUtil.initSPUtils(appcontext);

        //同时获取uuid存入sp中
//
//        String uniqueId = getDeviceIMEI(DBNFenpuApp.appcontext);
//        SharePreferenceUtil.setPrefString(Constant.UDID, uniqueId);

//        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
//
//        final String tmDevice, tmSerial, tmPhone, androidId;
//        tmDevice = "" + tm.getDeviceId();
//        tmSerial = "" + tm.getSimSerialNumber();
//        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
//
//        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());

    }

//    public boolean isPhone(Context context) {
//        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        return tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
//    }
//
//    public String getDeviceIMEI(Context context) {
//        String deviceId;
//        if (isPhone(context)) {
//            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//            deviceId = tm.getDeviceId();
//        } else {
//            deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
//        }
//        return deviceId;
//    }
    //


//    public static void addActivity(Activity activity) {
//        activityList.add(activity);
//    }
//    public static void removeActivity(Activity activity) {
//        activityList.remove(activity);
//    }

    public static void exit() {
//        for (Activity activity : activityList) {
//            if (activity != null && !activity.isFinishing()) {
//                activity.finish();
//            }
//        }
        File file=appcontext.getExternalCacheDir();
//        File file = new File(appcontext.getExternalCacheDir().getAbsolutePath());
        if (file.exists()) {
            File[] files = file.listFiles();
            for (File file1 : files) {
                file1.delete();
            }
        }
        cachedThreadPool.shutdown();

        System.exit(0);

    }

    public static ExecutorService getCachedThreadPool() {
        return cachedThreadPool;
    }

    public static void setCachedThreadPool(ExecutorService cachedThreadPool) {
        DBNFenpuApp.cachedThreadPool = cachedThreadPool;
    }

    public static boolean isApkDebugable(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {

        }
        return false;
    }
}
