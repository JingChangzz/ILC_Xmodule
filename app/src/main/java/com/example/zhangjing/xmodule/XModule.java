package com.example.zhangjing.xmodule;

import android.app.Activity;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by ZhangJing on 2017/11/20.
 *
 * exit point 部分
 */
public class XModule implements IXposedHookLoadPackage {
    String fileName = null;
    StringBuilder stringBuilder = null;
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //剔除系统应用
        if(lpparam.appInfo == null ||
                (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) !=0
                || lpparam.packageName.contains("xiaomi")) {
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

        //Activity 中的启动activity sink点(其他的方式实际都在ContextWrapper中)
        Class<?> cls = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        String[] activityMethods = {"startActivityForResult", "startActivityFromChild", "startActivityFromFragment",
            "startActivityIfNeeded", "startActivityForResultAsUser", "startActivityAsUser",
            "startActivity"};
        for (final String method : activityMethods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Activity -> " + method + ",");
                    if (param.args != null){
                        Intent i = null;
                        for (Object o : param.args){
                            if (o instanceof Intent){
                                i = (Intent)o;
                                if (i != null)
                                    stringBuilder.append(i.toUri(0) + ",");
                            }
                        }
                    }
                    stringBuilder.append("|");
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                    }
                    stringBuilder.append("\n");
                    XposedBridge.log(fileName + "$" + stringBuilder.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }

        //BroadcastReceiver中的sink点
        cls = XposedHelpers.findClass("android.content.BroadcastReceiver", lpparam.classLoader);
        String[] broadcastReceiverMethods = {"setResult", "setResultData"};

        for (final String method : broadcastReceiverMethods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("BroadcastReceiver -> " + method + ",");
                    if (param.args != null){
                        String s = null;
                        for (Object o : param.args){
                            if (o instanceof String){
                                s = (String)o;
                                if (s != null)
                                    stringBuilder.append("#data=" +s + ",");
                            }
                        }
                    }
                    stringBuilder.append("|");
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                    }
                    stringBuilder.append("\n");
                    XposedBridge.log(fileName + "$" + stringBuilder.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }


        cls = XposedHelpers.findClass("android.content.ContextWrapper", lpparam.classLoader);
        String[] contentWrappermethods = {"sendBroadcast", "startActivity", "startActivityAsUser",
                "startActivities", "sendBroadcast", "sendBroadcastMultiplePermissions",
                "sendOrderedBroadcast", "sendBroadcastAsUser", "sendOrderedBroadcastAsUser",
                "sendStickyBroadcast", "sendStickyOrderedBroadcastAsUser", "startService",
                "startServiceAsUser", "registerReceiver", "openFileOutput", "bindService"};

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
                    sb.append("ContextWrapper -> " + method + ",");

                    // Log current package name
                    //sb.append(AndroidAppHelper.currentPackageName() + ",");

                    // Log intent info
                    if (intent != null) {
                        sb.append(intent.toUri(0));
                        sb.append(",");
                    }

                    // Log extra info
                    if (method.equals("registerReceiver")) {
                        IntentFilter intentFilter = null;
                        BroadcastReceiver broadcastReceiver = null;
                        if (param.args != null && param.args.length > 1) {
                            Object o1 = param.args[0];
                            if (o1 instanceof BroadcastReceiver)
                                broadcastReceiver = (BroadcastReceiver) o1;
                            Object o = param.args[1];
                            if (o instanceof IntentFilter)
                                intentFilter = (IntentFilter) o;
                        }
                        sb.append("@@registerReceiver="+broadcastReceiver.getClass()+";action=" + intentFilter.getAction(0) + "@@");
                        sb.append(",");
                    }
                    if (method.equals("openFileOutput") || method.equals("openFileInput")) {
                        if (param.args != null && param.args.length > 1) {
                            sb.append("@@filename=" + param.args[0].toString() + "@@");
                            sb.append(",");
                        }
                    }
                    sb.append("|");
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        sb.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                    }
                    sb.append("\n");
                    XposedBridge.log(fileName + "$" + sb.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }

        //静态注册的广播，唤醒hook
        cls = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                stringBuilder = new StringBuilder();
                BroadcastReceiver br = null;
                if (param.args != null) {
                    for (Object o : param.args) {
                        if (o instanceof BroadcastReceiver)
                            br = (BroadcastReceiver) o;
                    }
                }
                //StringBuilder sb = new StringBuilder();
                stringBuilder.append("ReceiverDispatcher -> ");
                stringBuilder.append("staticBroadcast," + br.getClass().toString() + ";");
//                XposedBridge.log(fileName + "$" + stringBuilder.toString());
                super.beforeHookedMethod(param);

                Class clazz = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher$Args", lpparam.classLoader);
                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        Intent br = null;
                        if (param.args != null) {
                            for (Object o : param.args) {
                                if (o instanceof Intent)
                                    br = (Intent) o;
                            }
                        }
                        StringBuilder sb = new StringBuilder();
//                        sb.append(stringBuilder.toString() + "Args -> staticBroadcast,");
                        sb.append(stringBuilder.toString() + br.toUri(0) + ";hashcode="+System.identityHashCode(br));
                        sb.append("\n");
                        XposedBridge.log(fileName + "$" + sb.toString());
                        super.beforeHookedMethod(param);
                    }

                });
            }
        });

        //Intent中的source点
        cls = XposedHelpers.findClass("android.content.Intent", lpparam.classLoader);
        String[] intentMethods = {"getData", "getExtras", };

        for (final String method : intentMethods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Intent -> " + method + ",");

                    Intent result = (Intent)param.thisObject;
                    stringBuilder.append(result.toUri(0) + ";hashcode="+System.identityHashCode(result)+",");

                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        stringBuilder.append("|filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                    }
                    stringBuilder.append("\n");
                    XposedBridge.log(fileName + "$" + stringBuilder.toString());
                    super.afterHookedMethod(param);
                }
            });
        }

        //读写文件
        cls = XposedHelpers.findClass("java.io.FileOutputStream", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                String filepath = "null";
                if (param.args != null){
                    if (param.args[0] instanceof File){
                        File f = (File)param.args[0];
                        filepath = f.getAbsolutePath();
                    }else if (param.args[0] instanceof String){
                        filepath = (String) param.args[0];
                    }else{
                        return;
                    }
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(fileName + "$FileOutputStream -> " + filepath +"|");
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (StackTraceElement ele : elements) {
                    stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                }
                stringBuilder.append("\n");
                XposedBridge.log(stringBuilder.toString());
                super.beforeHookedMethod(param);
            }
        });

        //响应startActivity
        cls = XposedHelpers.findClass("android.app.Instrumentation", lpparam.classLoader);
        XposedBridge.hookAllMethods(cls, "callActivityOnCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Instrumentation -> callActivityOnCreate,");

                Activity r = (Activity)param.args[0];
                if (r != null) {
                    stringBuilder.append(r.toString()+";"+r.getIntent().toUri(0));
                }
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (StackTraceElement ele : elements) {
                    stringBuilder.append("|filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                }
                stringBuilder.append("\n");
                XposedBridge.log(fileName + "$" + stringBuilder.toString());
                super.afterHookedMethod(param);
            }
        });

//        //test
//        cls = XposedHelpers.findClass("com.example.zhangjing.recdevicetest.MainActivity", lpparam.classLoader);
//        XposedBridge.hookAllMethods(cls, "onCreate", new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                StringBuilder stringBuilder = new StringBuilder();
//                stringBuilder.append("Activity -> onCreate,");
//
//                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
//                for (StackTraceElement ele : elements) {
//                    stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
//                }
//                stringBuilder.append("\n");
//                XposedBridge.log(fileName + "$" + stringBuilder.toString());
//                super.beforeHookedMethod(param);
//            }
//        });

    }



    public void saveLog(StringBuilder sb){
        try {
            Log.i("xposed@@",fileName +".txt $$$$$");
            FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+ "/"+fileName +".txt", true);
            Writer out = new OutputStreamWriter(fos, "UTF-8");
            out.write(sb.toString());
            out.close();
            Log.i("xposed@@",fileName +".txt $$$$$");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
