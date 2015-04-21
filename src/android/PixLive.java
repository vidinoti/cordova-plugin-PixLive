package com.vidinoti.pixlive;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import android.view.View;
import android.widget.FrameLayout;

import com.vidinoti.android.vdarsdk.DeviceCameraImageSender;
import com.vidinoti.android.vdarsdk.VDARAnnotationView;
import com.vidinoti.android.vdarsdk.VDARSDKController;
import com.vidinoti.android.vdarsdk.VDARSDKControllerEventReceiver;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.Callable;

/**
 * This class echoes a string called from JavaScript.
 */
public class PixLive extends CordovaPlugin implements VDARSDKControllerEventReceiver {

    private static final String TAG ="PixLiveCordova";

    /** Your Project ID in Google APIs Console for Push Notification (GCM) */
    //private static final String GOOGLE_API_PROJECT_ID_FOR_NOTIFICATIONS = "000000";

    private HashMap<Integer, VDARAnnotationView> arViews = new HashMap<Integer, VDARAnnotationView>();

    private DeviceCameraImageSender imageSender = null;

    static void startSDK(final Context c, final String storage, final String licenseKey) {

        if(VDARSDKController.getInstance()!=null) {
            return;
        }
        VDARSDKController.startSDK(c, storage,  licenseKey);


        /* Comment out to disable QR code detection */
        VDARSDKController.getInstance().setEnableCodesRecognition(true);
        //  VDARRemoteController.getInstance().setUseRemoteTestServer(true);

        /* Enable push notifications */
        /* ------------------------- */

        /* See the documentation at http://doc.vidinoti.com/vdarsdk/web/android/latest for instructions on how to setup it */
        /* You need your app project ID from the Google APIs Console at https://code.google.com/apis/console */
        //VDARSDKController.getInstance().setNotificationsSupport(true, GOOGLE_API_PROJECT_ID_FOR_NOTIFICATIONS);

        /*VDARSDKController.getInstance().setNotificationFactory(new NotificationFactory() {

            @Override
            public Notification createNotification(String title, String message, String notificationID) {
                Intent appintent = new Intent(c, MainActivity.class);

                appintent.putExtra("nid", notificationID);
                appintent.putExtra("remote", false);

                PendingIntent contentIntent = PendingIntent.getActivity(c, 0,
                        appintent, PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(c)
                                .setSmallIcon(R.drawable.ic_launcher)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setContentIntent(contentIntent)
                                .setAutoCancel(true)
                                .setVibrate(new long[] { 100, 200, 200, 400 })
                                .setLights(Color.BLUE, 500, 1500);

                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                return mBuilder.getNotification();
            }
        });*/

    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("init") && args.length()>=2) {
            String url = args.getString(0);
            String key = args.getString(1);
            this.init(url,key, callbackContext);
            return true;
        } else if (action.equals("createARView") && args.length()>=5) {
            int x = args.getInt(0);
            int y = args.getInt(1);
            int width = args.getInt(2);
            int height = args.getInt(3);
            int ctrlID = args.getInt(4);
            this.createARView(x, y, width, height, ctrlID, callbackContext);
            return true;
        } else if (action.equals("beforeLeave") && args.length()>=1) {
            int ctrlID = args.getInt(0);
            this.beforeLeave(ctrlID, callbackContext);
            return true;
        } else if (action.equals("afterLeave") && args.length()>=1) {
            int ctrlID = args.getInt(0);
            this.afterLeave(ctrlID, callbackContext);
            return true;
        } else if (action.equals("beforeEnter") && args.length()>=1) {
            int ctrlID = args.getInt(0);
            this.beforeEnter(ctrlID, callbackContext);
            return true;
        } else if (action.equals("afterEnter") && args.length()>=1) {
            int ctrlID = args.getInt(0);
            this.afterEnter(ctrlID, callbackContext);
            return true;
        }else if (action.equals("destroy") && args.length()>=1) {
            int ctrlID = args.getInt(0);
            this.destroy(ctrlID, callbackContext);
            return true;
        }else if (action.equals("resize") && args.length()>=5) {
            int x = args.getInt(1);
            int y = args.getInt(2);
            int width = args.getInt(3);
            int height = args.getInt(4);
            int ctrlID = args.getInt(0);
            this.resize(ctrlID, x, y, width, height, callbackContext);
            return true;
        }
        return false;
    }


    private void beforeLeave(final int ctrlID, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView view = arViews.get(ctrlID);

                if (view != null) {
                    view.onPause();
                    VDARSDKController.getInstance().onPause();
                    view.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private void afterLeave(final int ctrlID, CallbackContext callbackContext) {

    }

    private void beforeEnter(final int ctrlID, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView view = arViews.get(ctrlID);

                if (view != null) {

                    VDARSDKController.getInstance().onResume();
                    view.onResume();

                    view.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void afterEnter(final int ctrlID, CallbackContext callbackContext) {

    }


    private void resize(final int x, final int y, final int width, final int height, final int ctrlID, CallbackContext callbackContext)
    {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView annotationView = arViews.get(ctrlID);

                if (annotationView != null)

                {

                    DisplayMetrics displaymetrics = new DisplayMetrics();

                    cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

                    // Add the view to the hierarchy
                    //FrameLayout frameLayout = (FrameLayout) webView.getParent().getParent();

                    annotationView.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) Math.round(width * displaymetrics.scaledDensity), (int) Math.round(height * displaymetrics.scaledDensity));
                    params.leftMargin = (int) Math.round(x * displaymetrics.scaledDensity);
                    params.topMargin = (int) Math.round(y * displaymetrics.scaledDensity);

                    annotationView.setLayoutParams(params);
                }
            }
        });

    }


    private void destroy(final int ctrlID, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView view = arViews.get(ctrlID);

                if (view != null) {

                    if (view.getParent() != null) {
                        ((FrameLayout) view.getParent()).removeView(view);
                    }

                    arViews.remove(ctrlID);
                }
            }
        });
    }


    private void createARView(final int x,final int y, final int width, final int height, final int ctrlID, final CallbackContext callbackContext) {

        if (!DeviceCameraImageSender.doesSupportDirectRendering()) {
            VDARSDKController.log(Log.ERROR,TAG,"This device is not supporting SurfaceView and is therefore not supported with PixLive SDK.");
            throw new RuntimeException("Device not supporting direct rendering");
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                try {
                    imageSender = new DeviceCameraImageSender(null);
                } catch (IOException e) {
                    VDARSDKController.log(Log.ERROR, TAG, Log.getStackTraceString(e));
                }

                VDARSDKController.getInstance().setImageSender(imageSender);

                VDARAnnotationView annotationView = new VDARAnnotationView(cordova.getActivity());

                DisplayMetrics displaymetrics = new DisplayMetrics();

                cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);


                // Add the view to the hierarchy
                FrameLayout frameLayout = (FrameLayout) webView.getParent().getParent();

                annotationView.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int)Math.round(width*displaymetrics.scaledDensity), (int)Math.round(height*displaymetrics.scaledDensity));
                params.leftMargin = (int)Math.round(x*displaymetrics.scaledDensity);
                params.topMargin = (int)Math.round(y*displaymetrics.scaledDensity);

                annotationView.setLayoutParams(params);

                frameLayout.addView(annotationView);

                arViews.put(ctrlID, annotationView);

                VDARSDKController.getInstance().setActivity(cordova.getActivity());

                VDARSDKController.getInstance().onResume();

                annotationView.onResume();

            }
        });
    }

    private void init(String storageURL, String licenseKey, CallbackContext callbackContext) {

        try {
            startSDK(cordova.getActivity(), new URL(storageURL).getPath(), licenseKey);
        } catch(MalformedURLException e) {
            VDARSDKController.log(Log.ERROR,TAG,"Invalid storage URL: "+storageURL+". PixLive SDK cannot start.");
            return;
        }

        VDARSDKController.getInstance().setEnableCodesRecognition(true);

        VDARSDKController.getInstance().setActivity(cordova.getActivity());
        VDARSDKController.getInstance().registerEventReceiver(this);

    }

    public void onCodesRecognized(java.util.ArrayList<com.vidinoti.android.vdarsdk.VDARCode> arrayList) {

    }

    public void onFatalError(java.lang.String s) {

    }

    public void onPresentAnnotations() {

    }

    public void onAnnotationsHidden() {

    }

    public void onTrackingStarted(int i, int i1) {

    }

    public void onEnterContext(com.vidinoti.android.vdarsdk.VDARContext vdarContext) {

    }

    public void onExitContext(com.vidinoti.android.vdarsdk.VDARContext vdarContext) {

    }
}
