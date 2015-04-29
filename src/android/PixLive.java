package com.vidinoti.pixlive;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.vidinoti.android.vdarsdk.DeviceCameraImageSender;
import com.vidinoti.android.vdarsdk.NotificationCompat;
import com.vidinoti.android.vdarsdk.NotificationFactory;
import com.vidinoti.android.vdarsdk.VDARAnnotationView;
import com.vidinoti.android.vdarsdk.VDARCode;
import com.vidinoti.android.vdarsdk.VDARCodeType;
import com.vidinoti.android.vdarsdk.VDARPrior;
import com.vidinoti.android.vdarsdk.VDARRemoteController;
import com.vidinoti.android.vdarsdk.VDARSDKController;
import com.vidinoti.android.vdarsdk.VDARSDKControllerEventReceiver;
import com.vidinoti.android.vdarsdk.VDARTagPrior;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * This class echoes a string called from JavaScript.
 */
public class PixLive extends CordovaPlugin implements VDARSDKControllerEventReceiver {

    private static final String TAG ="PixLiveCordova";

    class TouchInterceptorView extends FrameLayout {
        public boolean touchEnabled = true;

        private boolean intercepting = false;

        private View.OnTouchListener privateListener = null;

        public TouchInterceptorView(android.content.Context context) {
            super(context);
        }

        public void setTouchEnabled(boolean val) {
            touchEnabled = val;
        }

        private int getRelativeLeft(View myView) {
            if (myView.getParent() == myView.getRootView())
                return myView.getLeft();
            else
                return myView.getLeft() + getRelativeLeft((View) myView.getParent());
        }

        private int getRelativeTop(View myView) {
            if (myView.getParent() == myView.getRootView())
                return myView.getTop();
            else
                return myView.getTop() + getRelativeTop((View) myView.getParent());
        }

        @Override
        public boolean onInterceptTouchEvent (MotionEvent ev) {

            //We are already intercepting this
            if(intercepting && ev.getAction() != MotionEvent.ACTION_DOWN) {
                return true;
            }

            intercepting = false;

            if(!touchEnabled || arViews.size()==0) {
                return false;
            }

            int thisViewLeft = getRelativeLeft(this);
            int thisViewTop = getRelativeTop(this);

            //Check if we fall into one AR view

            for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
                VDARAnnotationView view = s.getValue();

                if(view.getVisibility() != View.VISIBLE || view.getParent()==null) {
                    continue;
                }

                //WARNING: We assume here that the AR view share the same parent as this view.

                float arViewX = view.getLeft();
                float arViewY = view.getTop();

                for(int i=0;i<ev.getPointerCount();i++) {

                    float xPos = ev.getX(i) - thisViewLeft;
                    float yPos = ev.getY(i) - thisViewTop;

                    if(xPos>=arViewX && xPos < arViewX + view.getWidth() && yPos>=arViewY && yPos < arViewY + view.getHeight()) {
                        intercepting = true;
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public boolean onTouchEvent (MotionEvent ev) {
            if(!intercepting) {
                return false;
            } else {

                //Forward it to ar views
                for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
                    VDARAnnotationView view = s.getValue();

                    float arViewX = view.getLeft();
                    float arViewY = view.getTop();

                    ev.offsetLocation(-arViewX,-arViewY);

                    if(view.dispatchTouchEvent(ev)) {
                        return true;
                    }

                    ev.offsetLocation(arViewX,arViewY);
                }
                return false;
            }
        }
    }

    private HashMap<Integer, VDARAnnotationView> arViews = new HashMap<Integer, VDARAnnotationView>();

    private DeviceCameraImageSender imageSender = null;

    private TouchInterceptorView touchView = null;

    private CallbackContext eventHandler = null;


    protected void pluginInitialize() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (touchView == null)

                {
                    View v = webView.getView();


                    touchView = new TouchInterceptorView(cordova.getActivity());

                    touchView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    FrameLayout parent = ((FrameLayout) v.getParent());
                    parent.removeView(v);
                    touchView.addView(v);
                    parent.addView(touchView);

                    v.setBackgroundColor(Color.TRANSPARENT);

                    if (Build.VERSION.SDK_INT >= 11) {
                        v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    }
                }
            }
        });
    }

    static void startSDK(final Context c, String storage, String licenseKey) {

        if(VDARSDKController.getInstance()!=null) {
            return;
        }

        if(storage != null) {
            // Save the storage path in the settings
            c.getSharedPreferences("pixlive",Context.MODE_PRIVATE).edit().putString("pixlive.sdk.storagedir",storage);
        } else {
            storage = c.getSharedPreferences("pixlive",Context.MODE_PRIVATE).getString("pixlive.sdk.storagedir",null);
        }

        if(licenseKey != null) {
            // Save the storage path in the settings
            c.getSharedPreferences("pixlive",Context.MODE_PRIVATE).edit().putString("pixlive.sdk.licensekey",licenseKey);
        } else {
            licenseKey = c.getSharedPreferences("pixlive",Context.MODE_PRIVATE).getString("pixlive.sdk.licensekey",null);
        }

        if(storage == null || licenseKey == null) {
            Log.e(TAG,"Unable to start PixLive SDK without valid storage and license key.");
            return;
        }

        VDARSDKController.startSDK(c, storage,  licenseKey);

        /* Comment out to disable QR code detection */
        VDARSDKController.getInstance().setEnableCodesRecognition(true);

        VDARSDKController.getInstance().setNotificationFactory(new NotificationFactory() {

            @Override
            public Notification createNotification(String title, String message, String notificationID) {
                return createNotification(title,message,notificationID,true);
            }

            @Override
            public Notification createNotification(String title, String message, String notificationID, boolean needARView) {

                Intent appIntent = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());

                appIntent.putExtra("nid", notificationID);
                appIntent.putExtra("remote", false);
                appIntent.putExtra("needARView", needARView);

                PendingIntent contentIntent = PendingIntent.getActivity(c, 0,
                        appIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                ApplicationInfo ai = c.getApplicationInfo();

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(c)
                                .setSmallIcon(ai.icon!=0 ? ai.icon : android.R.drawable.star_big_off)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setContentIntent(contentIntent)
                                .setAutoCancel(true)
                                .setVibrate(new long[] { 100, 200, 200, 400 })
                                .setLights(Color.BLUE, 500, 1500);

                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                return mBuilder.getNotification();
            }
        });

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
            this.createARView(x, y, width, height, ctrlID, args.length()>=6 ? args.getBoolean(5) : true, callbackContext);
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
            this.resize(x, y, width, height,ctrlID, callbackContext);
            return true;
        }else if (action.equals("synchronize")) {
            ArrayList<String> list = new ArrayList<String>();

            if(args.length()>0) {
                JSONArray a = args.getJSONArray(0);
                for (int i=0; i<a.length(); i++) {
                    list.add( a.getString(i) );
                }
            }

            this.synchronize(list,callbackContext);
            return true;
        } else if (action.equals("enableTouch")) {
            this.enableTouch();
            return true;
        } else if (action.equals("disableTouch")) {
            this.disableTouch();
            return true;
        } else if (action.equals("setNotificationsSupport") && args.length()>=1) {
            this.setNotificationsSupport(args.getString(0));
            return true;
        } else if (action.equals("installEventHandler")) {
            this.installEventHandler(callbackContext);
            return true;
        }
        return false;
    }

    private void installEventHandler(CallbackContext callback) {
        this.eventHandler = callback;
    }


    private void setNotificationsSupport(String googleProjectKey) {
        VDARSDKController.getInstance().setNotificationsSupport(googleProjectKey!=null, googleProjectKey);
    }

    private void enableTouch() {
        if(touchView!=null) {
            touchView.setTouchEnabled(true);
        }
    }

    private void disableTouch() {
        if(touchView!=null) {
            touchView.setTouchEnabled(false);
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            if(view.getParent()!=null && view.getVisibility()==View.VISIBLE) {
                view.onPause();
                VDARSDKController.getInstance().onPause();
            }
        }
    }

    public void onDestroy() {
        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            touchView.removeView(view);
        }

        arViews.clear();
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            if(view.getParent()!=null && view.getVisibility()==View.VISIBLE) {
                view.onResume();
                VDARSDKController.getInstance().onResume();
            }
        }
    }

    private void synchronize(ArrayList<String> tags, final CallbackContext callbackContext) {
        final ArrayList<VDARPrior> priors = new ArrayList<VDARPrior>();

        for(String s : tags) {
            priors.add(new VDARTagPrior(s));
        }

        VDARSDKController.getInstance().addNewAfterLoadingTask(new Runnable() {
            @Override
            public void run() {
                VDARRemoteController.getInstance().syncRemoteContextsAsynchronouslyWithPriors(priors, new Observer() {
                    @Override
                    public void update(Observable observable, Object data) {
                        VDARRemoteController.ObserverUpdateInfo info = (VDARRemoteController.ObserverUpdateInfo) data;

                        if (info.isCompleted()) {
                            if(info.getError()==null) {
                                JSONArray ctx = new JSONArray(info.getFetchedContexts());

                                callbackContext.success(ctx);
                            } else {
                                callbackContext.error(info.getError());
                            }
                        }
                    }
                });
            }
        });
    }

    private void beforeLeave(final int ctrlID, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView view = arViews.get(ctrlID);

                if (view != null) {
                    view.onPause();
                    VDARSDKController.getInstance().onPause();
                    view.setVisibility(View.GONE);
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

                if (annotationView != null) {
                    DisplayMetrics displaymetrics = new DisplayMetrics();

                    cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

                    annotationView.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)annotationView.getLayoutParams();
                    params.leftMargin = (int) Math.round(x * displaymetrics.scaledDensity);
                    params.topMargin = (int) Math.round(y * displaymetrics.scaledDensity);
                    params.width = (int) Math.round(width * displaymetrics.scaledDensity);
                    params.height = (int) Math.round(height * displaymetrics.scaledDensity);

                    annotationView.setLayoutParams(params);

                    annotationView.requestLayout();
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
                        touchView.removeView(view);
                    }

                    arViews.remove(ctrlID);
                }
            }
        });
    }


    private void createARView(final int x,final int y, final int width, final int height, final int ctrlID, final boolean insertBelow, final CallbackContext callbackContext) {

        if (!DeviceCameraImageSender.doesSupportDirectRendering()) {
            VDARSDKController.log(Log.ERROR,TAG,"This device is not supporting SurfaceView and is therefore not supported with PixLive SDK.");
            throw new RuntimeException("Device not supporting direct rendering");
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                if(imageSender == null) {
                    try {
                        imageSender = new DeviceCameraImageSender(null);
                    } catch (IOException e) {
                        VDARSDKController.log(Log.ERROR, TAG, Log.getStackTraceString(e));
                    }


                    VDARSDKController.getInstance().setImageSender(imageSender);
                }

                VDARAnnotationView annotationView = new VDARAnnotationView(cordova.getActivity());

                DisplayMetrics displaymetrics = new DisplayMetrics();

                cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

                annotationView.setVisibility(View.VISIBLE);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int)Math.round(width*displaymetrics.scaledDensity), (int)Math.round(height*displaymetrics.scaledDensity));
                params.leftMargin = (int)Math.round(x*displaymetrics.scaledDensity);
                params.topMargin = (int)Math.round(y*displaymetrics.scaledDensity);

                annotationView.setLayoutParams(params);

                touchView.addView(annotationView,0);

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

    private String getCodeTypeAsString(VDARCodeType c) {
        switch (c) {
            default:
            case VDAR_CODE_TYPE_NONE      :   return "none";
            case VDAR_CODE_TYPE_EAN2      :   return "ean2";
            case VDAR_CODE_TYPE_EAN5      :   return "ean5";
            case VDAR_CODE_TYPE_EAN8      :   return "ean8";
            case VDAR_CODE_TYPE_UPCE      :   return "upce";
            case VDAR_CODE_TYPE_ISBN10    :   return "isbn10";
            case VDAR_CODE_TYPE_UPCA      :   return "upca";
            case VDAR_CODE_TYPE_EAN13     :   return "ean13";
            case VDAR_CODE_TYPE_ISBN13    :   return "isbn13";
            case VDAR_CODE_TYPE_COMPOSITE :   return "composite";
            case VDAR_CODE_TYPE_I25       :   return "i25";
            case VDAR_CODE_TYPE_CODE39    :   return "code39";
            case VDAR_CODE_TYPE_QRCODE    :   return "qrcode";
        }
    }

    public void onCodesRecognized(java.util.ArrayList<com.vidinoti.android.vdarsdk.VDARCode> arrayList) {
        if(this.eventHandler != null) {
            for(VDARCode code : arrayList) {
                if(!code.isSpecialCode()) {
                    JSONObject o = new JSONObject();

                    try {
                        o.put("type", "codeRecognize");
                        o.put("codeType", getCodeTypeAsString(code.getCodeType()));
                        o.put("code", code.getCodeData());
                    } catch (JSONException e) {

                    }

                    PluginResult p = new PluginResult(PluginResult.Status.OK, o);
                    p.setKeepCallback(true);
                    this.eventHandler.sendPluginResult(p);
                }
            }
        }
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
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "enterContext");
                o.put("context", vdarContext.getRemoteID());
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);
            this.eventHandler.sendPluginResult(p);
        }
    }

    public void onExitContext(com.vidinoti.android.vdarsdk.VDARContext vdarContext) {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "exitContext");
                o.put("context", vdarContext.getRemoteID());
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);
            this.eventHandler.sendPluginResult(p);
        }
    }
}
