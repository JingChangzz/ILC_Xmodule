package com.example.zhangjing.xmodule;

import android.app.AndroidAppHelper;
import android.content.Intent;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Created by ZhangJing on 2017/11/20.
 */
public class XModule implements IXposedHookLoadPackage {
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //剔除系统应用
//        if(lpparam.appInfo == null ||
//                (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) !=0) {
//            return;
//        }else {
            XposedBridge.log("Loaded app: " + lpparam.packageName);
            hookActivityManager(lpparam);
//        }
    }

    public void hookActivityManager(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cls = XposedHelpers.findClass("android.app.ActivityManagerProxy", lpparam.classLoader);
        String[] methods = {"startService", "bindService", "broadcastIntent", "startActivity",
                "startActivityAsUser", "startActivityAsCaller", "startActivityAndWait",
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

                    // Log action type
                    sb.append(param.method.getName() + "FromClient,");

                    // Log current package name
                    sb.append(AndroidAppHelper.currentPackageName() + ",");

                    Log.i("xposed******", sb.toString());
                    // Log intent info
                    if (intent != null)
                        sb.append(intent.toUri(0));
                    sb.append(",");

                    // Log extra info
                    if (method.equals("broadcastIntent")) {
                        sb.append(param.args[7]);
                    }
                    if (method.equals("getContentProvider")) {
                        sb.append(param.args[1]);
                    }
                    sb.append(",");
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        sb.append(ele.getFileName()+"."+ele.getClassName()+"."+ele.getMethodName() + "|");
                    }

//                    BufferedWriter out = null;
//                    try {
//                        out = new BufferedWriter(new OutputStreamWriter(
//                                new FileOutputStream("/sdcard/icc.log", true)));
//                        out.write(sb.toString() + "\n");
//                        out.close();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
                    XposedBridge.log(sb.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }

        if (!lpparam.packageName.equals("android"))
            return;
        cls = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", lpparam.classLoader);
        XposedBridge.hookAllMethods(cls, "startProcessLocked", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("startProcessLocked2018,");
                    sb.append(param.args[1] + ",");
                    sb.append(param.args[2] + ",");
                    sb.append(param.args[3] + ",");
                    sb.append(param.args[4] + ",");
                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        sb.append(ele.getFileName()+"."+ele.getClassName()+"."+ele.getMethodName() + "|");
                    }
                    BufferedWriter out = null;
                    try {
                        out = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream("/storage/emulated/0/Download/icc.log", true)));
                        out.write(sb.toString() + "\n");
                        out.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    XposedBridge.log(sb.toString());
                }
                super.beforeHookedMethod(param);
            }
        });

        cls = XposedHelpers.findClass("com.android.server.am.ActiveServices", lpparam.classLoader);
        XposedBridge.hookAllMethods(cls, "scheduleServiceRestartLocked", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("scheduleServiceRestartLocked");
                param.setResult(true);
                super.beforeHookedMethod(param);
            }
        });
    }
}
