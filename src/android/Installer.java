package com.mycompany.installer;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import java.io.File;
import android.util.Log;

/**
 * This class echoes a string called from JavaScript.
 */
public class Installer extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("install")) {
            String message = args.getString(0);
            this.install(message, callbackContext);
            return true;
        }
        return false;
    }

    private void install(String message, CallbackContext callbackContext) {
        if (TextUtils.isEmpty(message)) {
            callbackContext.error("need a file.");
            return;
        }
        File apkFile = new File(message);
        if (!apkFile.exists()) {
            callbackContext.error("invalid file.");
            return;
        }
        if (!message.startsWith(Environment.getExternalStorageDirectory().toString())) {
            callbackContext.error("the input file is not in sdcard folder. \nmaybe access need permission.");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API level 21 or higher, we need to use PackageInstaller
            
            PackageInstaller.Session session = null;
            try {
                Log.i(TAG, "JIntentActionInstallApk " + filename);

                PackageInstaller packageInstaller = MyAppActivity.getQtActivityInstance().getPackageManager().getPackageInstaller();
                Log.i(TAG, "JIntentActionInstallApk - got packageInstaller");
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                Log.i(TAG, "JIntentActionInstallApk - set SessionParams");
                int sessionId = packageInstaller.createSession(params);
                session = packageInstaller.openSession(sessionId);
                Log.i(TAG, "JIntentActionInstallApk - session opened");

                // Create an install status receiver.
                Context context = MyAppActivity.getQtActivityInstance().getApplicationContext();
                addApkToInstallSession(context, filename, session);
                Log.i(TAG, "JIntentActionInstallApk - apk added to session");

                Intent intent = new Intent(context, MyAppActivity.class);
                intent.setAction(MyAppActivity.PACKAGE_INSTALLED_ACTION);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                IntentSender statusReceiver = PendingIntent.getIntentSender();
                // Commit the session (this will start the installation workflow).
                session.commit(statusReceiver);
                Log.i(TAG, "JIntentActionInstallApk - commited");
                
            } catch (IOException e) {
                throw new RuntimeException("Couldn't install package", e);
            } catch (RuntimeException e) {
                if (session != null) {
                    session.abandon();
                }
                throw e;
            }

        } else {
            Uri apkUri = Uri.fromFile(apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            cordova.getActivity().startActivity(intent);
        }
    }

    private static void addApkToInstallSession(Context context, String filename, PackageInstaller.Session session)
    {
       Log.i(TAG, "addApkToInstallSession " + filename);
       // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
       // if the disk is almost full.
       try {
            OutputStream packageInSession = session.openWrite("package", 0, -1);
            InputStream input;
            Uri uri = Uri.parse(filename);
            input = context.getContentResolver().openInputStream(uri);

            if(input != null) {
               Log.i(TAG, "input.available: " + input.available());
               byte[] buffer = new byte[16384];
               int n;
               while ((n = input.read(buffer)) >= 0) {
                   packageInSession.write(buffer, 0, n);
               }
            }
            else {
                Log.i(TAG, "addApkToInstallSession failed");
                throw new IOException ("addApkToInstallSession");
            }
            packageInSession.close();  //need to close this stream
            input.close();             //need to close this stream
       }
       catch (Exception e) {
           Log.i(TAG, "addApkToInstallSession failed2 " + e.toString());
       }
   }
}
/*
public static void JIntentActionInstallApk(final String filename)
    {
        PackageInstaller.Session session = null;
        try {
            Log.i(TAG, "JIntentActionInstallApk " + filename);

            if(Build.VERSION.SDK_INT < 21) {
                //as PackageInstaller was added in API 21, let's use the old way of doing it prior to 21
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                Uri apkUri = Uri.parse(filename);
                Context context = MyAppActivity.getQtActivityInstance().getApplicationContext();
                ApplicationInfo appInfo = context.getApplicationInfo();
                intent.setData(apkUri);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false);
                intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                     appInfo.packageName);
                MyAppActivity.getQtActivityInstance().startActivity(intent);
            }
            else  {
                // API level 21 or higher, we need to use PackageInstaller
                PackageInstaller packageInstaller = MyAppActivity.getQtActivityInstance().getPackageManager().getPackageInstaller();
                Log.i(TAG, "JIntentActionInstallApk - got packageInstaller");
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                Log.i(TAG, "JIntentActionInstallApk - set SessionParams");
                int sessionId = packageInstaller.createSession(params);
                session = packageInstaller.openSession(sessionId);
                Log.i(TAG, "JIntentActionInstallApk - session opened");

                // Create an install status receiver.
                Context context = MyAppActivity.getQtActivityInstance().getApplicationContext();
                addApkToInstallSession(context, filename, session);
                Log.i(TAG, "JIntentActionInstallApk - apk added to session");

                Intent intent = new Intent(context, MyAppActivity.class);
                intent.setAction(MyAppActivity.PACKAGE_INSTALLED_ACTION);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                IntentSender statusReceiver = pendingIntent.getIntentSender();
                // Commit the session (this will start the installation workflow).
                session.commit(statusReceiver);
                Log.i(TAG, "JIntentActionInstallApk - commited");
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't install package", e);
        } catch (RuntimeException e) {
            if (session != null) {
                session.abandon();
            }
            throw e;
        }
    }

    private static void addApkToInstallSession(Context context, String filename, PackageInstaller.Session session)
    {
           Log.i(TAG, "addApkToInstallSession " + filename);
           // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
           // if the disk is almost full.
           try {
                OutputStream packageInSession = session.openWrite("package", 0, -1);
                InputStream input;
                Uri uri = Uri.parse(filename);
                input = context.getContentResolver().openInputStream(uri);

                if(input != null) {
                   Log.i(TAG, "input.available: " + input.available());
                   byte[] buffer = new byte[16384];
                   int n;
                   while ((n = input.read(buffer)) >= 0) {
                       packageInSession.write(buffer, 0, n);
                   }
                }
                else {
                    Log.i(TAG, "addApkToInstallSession failed");
                    throw new IOException ("addApkToInstallSession");
                }
                packageInSession.close();  //need to close this stream
                input.close();             //need to close this stream
           }
           catch (Exception e) {
               Log.i(TAG, "addApkToInstallSession failed2 " + e.toString());
           }
   }

*/