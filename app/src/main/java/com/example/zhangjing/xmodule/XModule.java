package com.example.zhangjing.xmodule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ZhangJing on 2017/11/20.
 *
 * exit point 部分
 */
public class XModule implements IXposedHookLoadPackage {
    String packageName = null;
    StringBuilder stringBuilder = null;
    public static int writeFileTimes = 0;
    public static int readFileTimes = 0;
    public static Context context = null;

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //剔除系统应用
        if(lpparam.appInfo == null ||
                (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) !=0
                ||
                lpparam.packageName.contains("xiaomi") || lpparam.packageName.contains("xposed")) {
            return;
        }else {
            XposedBridge.log("Loaded app: " + lpparam.packageName);
//            Date d = new Date();
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
//            String dateNowStr = sdf.format(d);
            packageName = lpparam.packageName;
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
                    XposedBridge.log(packageName + "$" + stringBuilder.toString());
                }
            });
        }

        //BroadcastReceiver中的sink点
        cls = XposedHelpers.findClass("android.content.BroadcastReceiver", lpparam.classLoader);
        final String[] broadcastReceiverMethods = {"setResult", "setResultData"};

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
                    XposedBridge.log(packageName + "$" + stringBuilder.toString());
                }
            });
        }

        //响应startActivity
        cls = XposedHelpers.findClass("android.app.Instrumentation", lpparam.classLoader);
        XposedBridge.hookAllMethods(cls, "callActivityOnCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Instrumentation -> callActivityOnCreate,");

                Activity r = (Activity)param.args[0];
                if (r != null) {
                    stringBuilder.append(r.toString()+";"+r.getIntent().toUri(0));
                    context = r;
                }
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (StackTraceElement ele : elements) {
                    stringBuilder.append("|filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                }
                stringBuilder.append("\n");
                XposedBridge.log(packageName + "$" + stringBuilder.toString());
            }
        });

        cls = XposedHelpers.findClass("android.content.ContextWrapper", lpparam.classLoader);
        String[] contentWrappermethods = {"sendBroadcast", "startActivity", "startActivityAsUser",
                "startActivities", "sendBroadcast", "sendBroadcastMultiplePermissions",
                "sendOrderedBroadcast", "sendBroadcastAsUser", "sendOrderedBroadcastAsUser",
                "sendStickyBroadcast", "sendStickyOrderedBroadcastAsUser", "startService",
                "startServiceAsUser", "registerReceiver", "openFileOutput", "bindService","openFileInput"};

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

                    // Log intent info
                    if (intent != null) {
                        sb.append(intent.toUri(0));
                        sb.append(",");
                        saveCore(packageName, "intent" + intent.getAction());
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
                    if (method.equals("openFileInput")){
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
                    XposedBridge.log(packageName + "$" + sb.toString());
                }
            });
        }

        //接收广播，hook
        cls = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                stringBuilder = new StringBuilder();
                BroadcastReceiver br = null;
                if (param.args != null) {
                    for (Object o : param.args) {
                        if (o instanceof BroadcastReceiver)
                            br = (BroadcastReceiver) o;
                    }
                }
                stringBuilder.append("ReceiverDispatcher -> ");
                stringBuilder.append("staticBroadcast," + br.getClass().toString() + ";");
//                XposedBridge.log(fileName + "$" + stringBuilder.toString());
                super.beforeHookedMethod(param);

                Class clazz = XposedHelpers.findClass("android.app.LoadedApk$ReceiverDispatcher$Args", lpparam.classLoader);
                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                        Intent innent = null;
                        if (param.args != null) {
                            for (Object o : param.args) {
                                if (o instanceof Intent)
                                    innent = (Intent) o;
                            }
                        }
                        final Intent br = innent;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Looper mLooper;
                                Looper.prepare();
                                mLooper = Looper.myLooper();
                                final Handler handler = new Handler(mLooper) {
                                    @Override
                                    public void handleMessage(Message msg) {
                                        Map<String, String> v = getCore();
                                        Toast.makeText(context,packageName+"正试图接收来自"+v.get("Broadcast"+br.getAction())+"的广播", Toast.LENGTH_LONG);
                                        if (v.get("Broadcast"+br.getAction())!=null && !v.get("Broadcast"+br.getAction()).equals(packageName)){
                                            AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
                                            AlertDialog dialog = normalDialog.setMessage(packageName+"正试图接收来自"+v.get("Broadcast"+br.getAction())+"的广播:\n"+"要阻止吗？")
                                                    .setPositiveButton("是",
                                                            new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    //...To-do
                                                                    param.setResult(null);
                                                                }
                                                            })
                                                    .setNegativeButton("否",
                                                            new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    //...To-do
                                                                    StringBuilder sb = new StringBuilder();
                                                                    sb.append(stringBuilder.toString() + br.toUri(0) + ";hashcode="+System.identityHashCode(br));
                                                                    sb.append("\n");
                                                                    XposedBridge.log(packageName + "$" + sb.toString());
                                                                }
                                                            }).create();
                                            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_TOAST);
                                            dialog.setCanceledOnTouchOutside(false);//点击屏幕不消失

                                            dialog.show();
                                        }
                                    }
                                };
                                handler.sendEmptyMessage(0);
                                Looper.loop();
                            }
                        }).run();
                    }

                });
            }
        });

        //Intent中的source点
        cls = XposedHelpers.findClass("android.content.Intent", lpparam.classLoader);
        final String[] intentMethods = {"getData", "getExtras", };

        for (final String method : intentMethods) {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Intent -> " + method + ",");

                    Intent result = (Intent)param.thisObject;
                    HashMap<String, String> v = getCore();
                    if (v.get("intent"+result.getAction())!=null && !v.get("intent"+result.getAction()).equals(packageName)) {
                        AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
                        AlertDialog dialog = normalDialog.setMessage(packageName+"正试图通过与"+v.get("intent"+result.getAction())+"通信的intent中获取信息，要阻止访问吗？")
                                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //...To-do
                                        param.setResult(null);
                                    }
                                })
                                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //...To-do
                                        param.setResult(null);
                                    }
                                }).create();
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                        dialog.setCanceledOnTouchOutside(false);//点击屏幕不消失
                        dialog.show();
                    }

//                        normalDialog.setTitle("警告");
//                        normalDialog.setMessage(packageName+"正试图通过与"+v.get("intent"+result.getAction())+"通信的intent中获取信息，要阻止访问吗？");
//                        normalDialog.setPositiveButton("是",
//                                new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialog, int which) {
//                                        //...To-do
//                                        param.setResult(null);
//                                    }
//                                });
//                        normalDialog.setNegativeButton("否",
//                                new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialog, int which) {
//                                        //...To-do
//                                    }
//                                });
//                        normalDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
//                        normalDialog.setCanceledOnTouchOutside(false);//点击屏幕不消失
//                        // 显示
//                        normalDialog.show();
//                    }

//                    stringBuilder.append(result.toUri(0) + ";hashcode="+System.identityHashCode(result)+",");
//
//                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
//                    for (StackTraceElement ele : elements) {
//                        stringBuilder.append("|filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
//                    }
//                    stringBuilder.append("\n");
//                    XposedBridge.log(packageName + "$" + stringBuilder.toString());
//                    super.afterHookedMethod(param);
                }
            });
        }

        //写文件
        cls = XposedHelpers.findClass("java.io.FileOutputStream", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                if (writeFileTimes > 1){
                    writeFileTimes = 0;
                    return;
                }
                String filepath = "null";
                if (param.args != null){
                    if (param.args[0] instanceof File){
                        File f = (File)param.args[0];
                        filepath = f.getAbsolutePath();
                        if (filepath.contains("ilc.txt")){
                            return;
                        }
                    }else{
                        return;
                    }
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(packageName + "$FileOutputStream -> " + filepath +"|");
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (StackTraceElement ele : elements) {
                    stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                }
                stringBuilder.append("\n");
                XposedBridge.log(stringBuilder.toString());
                writeFileTimes++;
                saveCore(packageName, "file"+filepath);
                super.beforeHookedMethod(param);
            }
        });

        //读文件
        cls = XposedHelpers.findClass("java.io.FileInputStream", lpparam.classLoader);
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                if (readFileTimes > 1){
                    readFileTimes = 0;
                    return;
                }
                String filepath = "null";
                if (param.args != null){
                    if (param.args[0] instanceof File){
                        File f = (File)param.args[0];
                        filepath = f.getAbsolutePath();
                        if (filepath.contains("ilc.txt")){
                            return;
                        }
                    }else {
                        return;
                    }
                }
                HashMap<String, String> v = getCore();
                if (v.get("file"+filepath)!=null && !v.get("file"+filepath).equals(packageName)){
                    final String f = filepath;
                    AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
                    normalDialog.setTitle("警告");
                    normalDialog.setMessage(packageName+"正试图访问"+v.get("file"+filepath)+"的文件:\n"+filepath+"\n"+"要阻止访问吗？");
                    normalDialog.setPositiveButton("是",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //...To-do
                                    param.setResult(null);
                                }
                            });
                    normalDialog.setNegativeButton("否",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //...To-do
                                    StringBuilder stringBuilder = new StringBuilder();
                                    stringBuilder.append(packageName + "$FileInputStream -> " + f +"|");
                                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                                    for (StackTraceElement ele : elements) {
                                        stringBuilder.append("filename="+ele.getFileName() + ";" + "classname="+ele.getClassName() + ";" + "methodname="+ele.getMethodName() + "|");
                                    }
                                    stringBuilder.append("\n");
                                    XposedBridge.log(stringBuilder.toString());
                                    readFileTimes++;
                                }
                            });
                    // 显示
                    normalDialog.show();
                }
            }
        });

        //test ContentProvider
        cls = XposedHelpers.findClass("android.content.ContentProvider", lpparam.classLoader);
        String[] contentProviderMethods = {"insert", "bulkInsert", "update",
                    "query", "openFile"};
        for (final String method : contentProviderMethods) {
            XposedBridge.hookAllMethods(cls, "onCreate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("ContentProvider -> " + method + ",");
                    for (Object ar : param.args){
                        if (ar instanceof Uri){
                            stringBuilder.append(((Uri)ar).toString());
                            break;
                        }
                    }

                    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                    for (StackTraceElement ele : elements) {
                        stringBuilder.append("|filename=" + ele.getFileName() + ";" + "classname=" + ele.getClassName() + ";" + "methodname=" + ele.getMethodName() + "|");
                    }
                    stringBuilder.append("\n");
                    XposedBridge.log(packageName + "$" + stringBuilder.toString());
                    super.beforeHookedMethod(param);
                }
            });
        }
    }


    //将通信的联系点存入文件。因为对每个app的hook都是单独的进程
    public void saveCore(String appName, String core){
        try {
            FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+ "/ilc.txt", true);
            Writer out = new OutputStreamWriter(fos, "UTF-8");
            out.write(core+"," + appName + "\n");
            out.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, String> getCore(){
        String line="";
        HashMap<String, String> result = new HashMap();
        try {
            FileInputStream fos = new FileInputStream(Environment.getExternalStorageDirectory().getPath()+ "/ilc.txt");
            BufferedReader out = new BufferedReader(new InputStreamReader(fos, "UTF-8"));
            while ((line=out.readLine())!=null) {
                result.put(line.split(",")[0], line.split(",")[1]);
            }
            out.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    public void saveLog(StringBuilder sb){
        try {
            Log.i("xposed@@",packageName +".log $$$$$");
            FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+ "/"+packageName +".log", true);
            Writer out = new OutputStreamWriter(fos, "UTF-8");
            out.write(sb.toString());
            out.close();
            fos.close();
            Log.i("xposed@@",packageName +".log $$$$$");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
