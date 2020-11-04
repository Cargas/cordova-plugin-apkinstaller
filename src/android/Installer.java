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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import android.util.Log;
import android.app.Activity;

import android.content.Context;

/**
 * This class echoes a string called from JavaScript.
 */
public class Installer extends CordovaPlugin {

     private static final String PACKAGE_INSTALLED_ACTION =
            "com.example.android.apis.content.SESSION_API_PACKAGE_INSTALLED";

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API level 21 or higher, we need to use PackageInstaller
            
            PackageInstaller.Session session = null;
            try {
                Activity AppActivity = this.cordova.getActivity();
                Context context = AppActivity.getApplicationContext();
                PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = packageInstaller.createSession(params);
                session = packageInstaller.openSession(sessionId);

                // Create an install status receiver.
                addApkToInstallSession(context, message, session);

                Intent intent = new Intent(context, AppActivity.getClass());
                intent.setAction(this.PACKAGE_INSTALLED_ACTION);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                IntentSender statusReceiver = pendingIntent.getIntentSender();

                // Commit the session (this will start the installation workflow).
                session.commit(statusReceiver);
                
            } catch (IOException e) {
                throw new RuntimeException("Couldn't install package", e);
            } catch (FileNotFoundException e) {
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

    private static void addApkToInstallSession(Context context, String filename, PackageInstaller.Session session) throws IOException, FileNotFoundException
    {
       // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
       // if the disk is almost full.
       try (OutputStream packageInSession = session.openWrite("package", 0, -1);
            InputStream input = context.getContentResolver().openInputStream(Uri.parse(filename))) {

           byte[] buffer = new byte[16384];
           int n;
           while ((n = input.read(buffer)) >= 0) {
               packageInSession.write(buffer, 0, n);
           }
        }
            /*session.fsync(packageInSession);
            packageInSession.close();  //need to close this stream
            input.close();             //need to close this stream
            System.gc();*/
       
   }
}