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
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

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
import java.io.FileNotFoundException;
import java.lang.SecurityException;
import android.util.Log;
import android.app.Activity;
import android.os.Bundle;

import android.widget.Toast;
import android.content.Context;

/**
 * This class echoes a string called from JavaScript.
 */
public class Installer extends CordovaPlugin {

     private static final String PACKAGE_INSTALLED_ACTION =
            "com.example.android.apis.content.SESSION_API_PACKAGE_INSTALLED";
    private Activity AppActivity = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        AppActivity = cordova.getActivity();
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API level 21 or higher, we need to use PackageInstaller
            
            PackageInstaller.Session session = null;
            try {
                Context context = AppActivity.getApplicationContext();
                Toast.makeText(context, "context created", Toast.LENGTH_SHORT).show();
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
                callbackContext.success("launched installer!");
            } catch (IOException e) {
                if (session != null) {
                    session.abandon();
                }
                throw new RuntimeException("Couldn't install package, IOException: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                if (session != null) {
                    session.abandon();
                }
                throw new RuntimeException("Couldn't install package, Runtime Exception: " + e.getMessage(), e);
            } catch (SecurityException e) {
                if (session != null) {
                    session.abandon();
                }
                throw new RuntimeException("Couldn't install package, Security Exception: " + e.getMessage(), e);
            }

        } else {
            File apkFile = new File(message);
            if (!apkFile.exists()) {
                callbackContext.error("invalid file.");
                return;
            }
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

   /**
     * Triggered on new intent
     *
     * @param intent
     */
    @Override
    public void onNewIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (PACKAGE_INSTALLED_ACTION.equals(intent.getAction())) {
            int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
            String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    // This test app isn't privileged, so the user has to confirm the install.
                    Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
                    startActivity(confirmIntent);
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    Toast.makeText(this, "Install succeeded!", Toast.LENGTH_SHORT).show();
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    Toast.makeText(this, "Install failed! " + status + ", " + message,
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(this, "Unrecognized status received from installer: " + status,
                            Toast.LENGTH_SHORT).show();
            }
        }
    }
}