package com.example.zhangjing.xmodule;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by ZhangJing on 2017/11/20.
 */
public class XModule implements IXposedHookLoadPackage {
    StringBuilder sbForStaticBro = new StringBuilder();
    String fileName = null;
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //剔除系统应用
        if(lpparam.appInfo == null ||
                (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) !=0) {
            return;
        }else {
            XposedBridge.log("Loaded app: " + lpparam.packageName);
//            Date d = new Date();
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
//            String dateNowStr = sdf.format(d);
            fileName = lpparam.packageName;
            hookActivityManager(lpparam);
        }
    }

    public void hookActivityManager(final XC_LoadPackage.LoadPackageParam lpparam) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        Class<?> cls = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                Log.i("xposed######", "ReceiverDispatcher$ReceiverDispatcher");
                BroadcastReceiver br = null;
                if (param.args != null){
                    for (Object o : param.args) {
                        if (o instanceof BroadcastReceiver)
                            br = (BroadcastReceiver) o;
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("ReceiverDispatcher -> ");
                sb.append("staticBroadcast," + br.getClass().toString());
                XposedBridge.log(fileName+"$" + sb.toString());
                super.beforeHookedMethod(param);
            }
        });

        Class clazz = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher$Args", lpparam.classLoader);
        XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                Log.i("xposed######", "Args$Args");
                Intent br = null;
                if (param.args != null){
                    for (Object o : param.args) {
                        if (o instanceof Intent)
                            br = (Intent) o;
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Args -> ");
                sb.append("staticBroadcast," + br.toUri(0));
                XposedBridge.log(fileName+"$" + sb.toString());
                super.beforeHookedMethod(param);
            }
        });

                cls = XposedHelpers.findClass("android.content.ContextWrapper", lpparam.classLoader);
        String[] contentWrappermethods = {"sendBroadcast", "startActivity", "startActivityAsUser",
                "startActivityForResult", "startActivities", "sendBroadcast", "sendBroadcastMultiplePermissions",
                "sendOrderedBroadcast", "sendBroadcastAsUser", "sendOrderedBroadcastAsUser",
                "sendStickyBroadcast", "sendStickyOrderedBroadcastAsUser", "startService",
                "startServiceAsUser", "registerReceiver"};

        for (final String method : contentWrappermethods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = null;
                    if (param.args != null) {
                        for (Object o : param.args) {
                            if (o instanceof Intent)
                                intent = (Intent) o;
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    // Log action type
                    sb.append("ContextWrapper -> "+param.method.getName() + ",");

                    // Log current package name
                    //sb.append(AndroidAppHelper.currentPackageName() + ",");

//                    Log.i("xposed ---> ", sb.toString());
                    // Log intent info
                    if (intent != null) {
                        sb.append(intent.toUri(0));
                        sb.append(",");
                    }

                    // Log extra info
                    if (method.equals("sendBroadcast") || method.equals("startService")
                            || method.equals("startService")) {
                        Intent result = null;
                        Object an = param.args[0];
                        if (an instanceof Intent)
                            result = (Intent) an;
                        sb.append("@@action=" + result.getAction() + "@@");
                        sb.append(",");
                    }
                    if (method.equals("registerReceiver")){
                        IntentFilter intentFilter = null;
                        if (param.args != null) {
                            Object o = param.args[1];
                            if (o instanceof IntentFilter)
                                intentFilter = (IntentFilter) o;
                        }
                        sb.append("@@action="+intentFilter.getAction(0)+"@@");
                        sb.append(",");
                    }

                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        sb.append(ele.getFileName()+"."+ele.getClassName()+"."+ele.getMethodName() + "|");
                    }
                    XposedBridge.log(fileName+"$" + sb.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }

        cls = XposedHelpers.findClass("android.app.ActivityManagerProxy", lpparam.classLoader);
        String[] methods = {"bindService", "broadcastIntent",
                "startActivityAsCaller", "startActivityAndWait",
                "getContentProvider"};

        for (final String method : methods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = null;
                    if (param.args != null) {
                        for (Object o : param.args) {
                            if (o instanceof Intent)
                                intent = (Intent) o;
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("ActivityManagerProxy -> ");
                    // Log action type
                    sb.append(param.method.getName() + ",");

                    // Log current package name
                    //sb.append(AndroidAppHelper.currentPackageName() + ",");

//                    Log.i("xposed******", sb.toString());
                    // Log intent info
                    if (intent != null)
                        sb.append(intent.toUri(0));
                    sb.append(",");

                    // Log extra info
                    if (method.equals("broadcastIntent")) {
                        sb.append("@@"+param.args[7]+"@@");
                        sb.append(",");
                    }
                    if (method.equals("getContentProvider")) {
                        for (Object o : param.args) {
                            sb.append("@@" + o + "@@");
                            sb.append(",");
                        }
                    }
                    if (method.equals("registerReceiver")) {

                        IntentFilter intentFilter = null;
                        if (param.args != null) {
                            Object o = param.args[3];
                            if (o instanceof IntentFilter)
                                intentFilter = (IntentFilter) o;
                        }
                        sb.append("@@action="+intentFilter.getAction(0)+"@@");
                        sb.append(",");
                    }
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        sb.append(ele.getFileName()+"."+ele.getClassName()+"."+ele.getMethodName() + "|");
                    }

                    XposedBridge.log(fileName+"$" + sb.toString());
                    super.beforeHookedMethod(param);
//                    saveLog(sb);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (method.equals("registerReceiver")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("afterHookedMethod" + param.method.getName()+":");
                        Intent result = null;
                        Object an = param.getResult();
                        if (an instanceof Intent) {
                            result = (Intent) an;
                            sb.append(result.toUri(0) + "###");
                            if (result != null) {
                                sb.append(result.getAction());
                                sb.append("###");
                            }
                        }else {
                            if (an != null)
                                sb.append(an.toString());
                        }
                        XposedBridge.log(fileName+"$" + sb.toString());
//                        saveLog(sb);
                    }
                    super.afterHookedMethod(param);
                }
            });
        }

//        cls = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", lpparam.classLoader);
//        if (cls != null) {
//            XposedBridge.hookAllMethods(cls, "startProcessLocked", new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    if (param.args.length == 6) {
//                        StringBuilder sb = new StringBuilder();
//                        sb.append("startProcessLocked2018,");
//                        sb.append(param.args[1] + ",");
//                        sb.append(param.args[2] + ",");
//                        sb.append(param.args[3] + ",");
//                        sb.append(param.args[4] + ",");
//                        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
//                        for (StackTraceElement ele : elements) {
//                            sb.append(ele.getFileName() + "." + ele.getClassName() + "." + ele.getMethodName() + "|");
//                        }
////                        saveLog(sb);
//                        XposedBridge.log(sb.toString());
//                    }
//                    super.beforeHookedMethod(param);
//                }
//            });
//        }

        cls = XposedHelpers.findClass("com.android.server.am.ActiveServices", lpparam.classLoader);
        XposedBridge.hookAllMethods(cls, "scheduleServiceRestartLocked", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                StringBuilder sb = new StringBuilder();
                sb.append("scheduleServiceRestartLocked");
                for (int i = 0; i < param.args.length; i++)
                    sb.append(param.args[i] + ",");
//                saveLog(sb);
                XposedBridge.log(fileName+"$" + sb.toString());
                param.setResult(true);
                super.beforeHookedMethod(param);
            }
        });

    }

    public void saveLog(StringBuilder sb){
        try {
            FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+ fileName +".txt", true);
            Writer out = new OutputStreamWriter(fos, "UTF-8");
            out.write(sb.toString());
            out.close();
            Log.i("xposed@@",fileName +".txt $$$$$");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
