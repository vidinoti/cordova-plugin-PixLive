
package com.vidinoti.pixlive;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Looper;
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
import com.vidinoti.android.vdarsdk.VDARContext;
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
import java.text.SimpleDateFormat;
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
            //Log.v(TAG,"touch: action:" + ev.getAction() + " touchEnabled: " + touchEnabled +" intercepting " + intercepting);

            if((!touchEnabled || arViews.size()==0)) {
                return super.onInterceptTouchEvent(ev);
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
                        return true;
                    }
                }
            }

            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent (MotionEvent ev) {

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

    private HashMap<Integer, VDARAnnotationView> arViews = new HashMap<Integer, VDARAnnotationView>();

    private DeviceCameraImageSender imageSender = null;

    private TouchInterceptorView touchView = null;

    private CallbackContext eventHandler = null;

    private ArrayList<Runnable> foregroundCallbacks = new ArrayList<Runnable>();

    private boolean activityActive = false;

    private boolean pageLoaded = false;


    protected void pluginInitialize() {

        startSDK(cordova.getActivity());

        VDARSDKController.getInstance().setEnableCodesRecognition(true);

        VDARSDKController.getInstance().setActivity(cordova.getActivity());
        VDARSDKController.getInstance().registerEventReceiver(this);

        VDARSDKController.getInstance().addNewAfterLoadingTask(new Runnable() {
            @Override
            public void run() {

                Intent intent = cordova.getActivity().getIntent();

                if (intent != null && intent.getExtras() != null
                        && intent.getExtras().getString("nid") != null) {

                    VDARSDKController.getInstance().processNotification(
                            intent.getExtras().getString("nid"),
                            intent.getExtras().getBoolean("remote"));
                }
            }
        });

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (touchView == null) {
                    View v = webView.getView();

                    FrameLayout parent = ((FrameLayout) v.getParent());
                    parent.removeView(v);

                    touchView = new TouchInterceptorView(cordova.getActivity());

                    touchView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    cordova.getActivity().setContentView(touchView);

                    touchView.addView(v);

                    v.setBackgroundColor(Color.TRANSPARENT);

                    if (Build.VERSION.SDK_INT <= 16) {
                       v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    }
                }
            }
        });
    }

    static void startSDK(final Context c) {

        if(VDARSDKController.getInstance()!=null) {
            return;
        }


        String storage = c.getApplicationContext().getFilesDir()
                .getAbsolutePath() + "/pixliveSDK";

        String licenseKey = null;

        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            licenseKey = bundle.getString("com.vidinoti.pixlive.LicenseKey");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG,"Unable to start PixLive SDK without valid storage and license key.");
            return;
        } catch (NullPointerException e) {
            Log.e(TAG,"Unable to start PixLive SDK without valid storage and license key.");
            return;
        }

        if(storage == null || licenseKey == null) {
            Log.e(TAG,"Unable to start PixLive SDK without valid storage and license key.");
            return;
        }

        VDARSDKController.startSDK(c, storage, licenseKey);

        /* Comment out to disable QR code detection */
        VDARSDKController.getInstance().setEnableCodesRecognition(true);

        VDARSDKController.getInstance().setNotificationFactory(new NotificationFactory() {

            @Override
            public Notification createNotification(String title, String message, String notificationID) {

                Intent appIntent = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());

                appIntent.putExtra("nid", notificationID);
                appIntent.putExtra("remote", false);

                PendingIntent contentIntent = PendingIntent.getActivity(c, 0,
                        appIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                ApplicationInfo ai = c.getApplicationInfo();

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(c)
                                .setSmallIcon(ai.icon != 0 ? ai.icon : android.R.drawable.star_big_off)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setContentIntent(contentIntent)
                                .setAutoCancel(true)
                                .setVibrate(new long[]{100, 200, 200, 400})
                                .setLights(Color.BLUE, 500, 1500);

                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                return mBuilder.getNotification();
            }
        });
    }

    public void onReset() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                pageLoaded = false;
                for (VDARAnnotationView view : arViews.values()) {
                    view.onPause();

                    if (view.getParent() != null) {
                        touchView.removeView(view);
                    }
                }
                arViews.clear();
            }
        });
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null && intent.getExtras() != null
                && intent.getExtras().getString("nid") != null && VDARSDKController.getInstance()!=null) {

            VDARSDKController.getInstance().processNotification(
                    intent.getExtras().getString("nid"),
                    intent.getExtras().getBoolean("remote"));
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("createARView") && args.length()>=5) {
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
        } else if (action.equals("presentNotificationsList")) {
            this.presentNotificationsList(callbackContext);
            return true;
        } else if (action.equals("openURLInInternalBrowser") && args.length()>=1) {
            this.openURLInInternalBrowser(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("getContexts")) {
            this.getContexts(callbackContext);
            return true;
        }else if (action.equals("activateContext") && args.length()>=1) {
            this.activateContext(args.getString(0),callbackContext);
            return true;
        }else if (action.equals("ignoreContext") && args.length()>=1) {
            this.ignoreContext(args.getString(0),callbackContext);
            return true;
        } else if (action.equals("pageLoaded")) {
            if(activityActive) {
                for(Runnable r : foregroundCallbacks) {
                    r.run();
                }

                foregroundCallbacks.clear();
            }
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

    private boolean isWebViewDestroyed() {

        if(webView == null) {
            return true;
        }

        if(Thread.currentThread().equals(Looper.getMainLooper().getThread())) {
            final String url = webView.getUrl();
            if (url == null ||
                    url.equals("about:blank")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {

        activityActive = false;

        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            if(view.getParent()!=null && view.getVisibility()==View.VISIBLE) {
                view.onPause();
            }
        }

        VDARSDKController.getInstance().getLocalizationManager().stopLocalization();
    }

    public void onDestroy() {
        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            touchView.removeView(view);
        }

        arViews.clear();

        this.eventHandler = null;
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {

        activityActive = true;

        for(Map.Entry<Integer,VDARAnnotationView> s : arViews.entrySet()) {
            VDARAnnotationView view = s.getValue();

            if(view.getParent()!=null && view.getVisibility()==View.VISIBLE) {
                view.onResume();
            }
        }

        VDARSDKController.getInstance().getLocalizationManager().startLocalization();

        if(pageLoaded) {
            for(Runnable r : foregroundCallbacks) {
                r.run();
            }

            foregroundCallbacks.clear();
        }
    }

    private void presentNotificationsList(final CallbackContext callbackContext) {

        if(0 == VDARSDKController.getInstance().getPendingNotifications().size()){
            if(!isWebViewDestroyed()) {
                callbackContext.error("empty");
            }
        }else{
            if(!isWebViewDestroyed()) {
                callbackContext.success();
            }
            VDARSDKController.getInstance().presentNotificationsList();
        }
    }

    private void openURLInInternalBrowser(String url, final CallbackContext callbackContext) {
        try {
            URL urlObj = new URL(url);

            VDARSDKController.getInstance().openURLInInternalBrowser(urlObj);
        } catch(MalformedURLException e) {

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
                                if(!isWebViewDestroyed()) {
                                    callbackContext.success(ctx);
                                }
                            } else {
                                if(!isWebViewDestroyed()) {
                                    callbackContext.error(info.getError());
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    private void activateContext(String contextId, final CallbackContext callbackContext) {
        VDARContext c = VDARSDKController.getInstance().getContext(contextId);
        if(c != null) {
            c.activate();
        }
    }

    private void ignoreContext(String contextId, final CallbackContext callbackContext) {
        VDARContext c = VDARSDKController.getInstance().getContext(contextId);
        if(c != null) {
            c.ignore();
        }
    }

    private void getContexts(final CallbackContext callbackContext) {
        final ArrayList<String> contextIds = VDARSDKController.getInstance().getAllContextIDs();
        JSONArray ret = new JSONArray();
        
        SimpleDateFormat format = new SimpleDateFormat("Z");

        for(String ctxId : contextIds) {
            VDARContext c = VDARSDKController.getInstance().getContext(ctxId);
            if(c != null) {
                JSONObject obj = new JSONObject();

                try {
                    obj.put("contextId",ctxId);
                    obj.put("name",c.getName());
                    obj.put("lastUpdate",format.format(c.getLastModifiedDate()));
                    obj.put("description",c.getDescription());
                    obj.put("notificationTitle",c.getNotificationTitle());
                    obj.put("notificationMessage",c.getNotificationMessage());
                    obj.put("imageThumbnailURL",c.getImageThumbnailURL());
                    obj.put("imageHiResURL",c.getImageHiResURL());
                } catch (JSONException e) {

                }
                
                ret.put(obj);
            }
        }
        if(!isWebViewDestroyed()) {
            callbackContext.success(ret);
        }
    }

    private void beforeLeave(final int ctrlID, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                VDARAnnotationView view = arViews.get(ctrlID);

                if (view != null) {
                    view.onPause();
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

                    view.onPause();

                    if (view.getParent() != null) {
                        touchView.removeView(view);
                    }

                    arViews.remove(ctrlID);
                }
            }
        });
    }


    private void createARView(final int x,final int y, final int width, final int height, final int ctrlID, final boolean insertBelow, final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                if(imageSender == null) {
                    try {
                        imageSender = new DeviceCameraImageSender();
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

                annotationView.onResume();

            }
        });
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
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "presentAnnotations");
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);
            this.eventHandler.sendPluginResult(p);
        }
    }

    public void onAnnotationsHidden() {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "hideAnnotations");
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);
            this.eventHandler.sendPluginResult(p);
        }
    }

    public void onTrackingStarted(int w, int h) {

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

    public void onRequireSynchronization(final ArrayList<com.vidinoti.android.vdarsdk.VDARPrior> priors) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if(PixLive.this.eventHandler != null) {
                    JSONObject o = new JSONObject();

                    try {
                        JSONArray arr = new JSONArray();

                        for(VDARPrior p : priors) {
                            if(p instanceof VDARTagPrior) {
                                arr.put(((VDARTagPrior)p).getTag());
                            }
                        }

                        o.put("type", "requireSync");
                        o.put("tags", arr);
                    } catch (JSONException e) {

                    }

                    PluginResult p = new PluginResult(PluginResult.Status.OK, o);
                    p.setKeepCallback(true);
                    PixLive.this.eventHandler.sendPluginResult(p);
                }
            }
        };


        if(activityActive && pageLoaded) {
            r.run();
        } else {
            foregroundCallbacks.add(r);
        }

    }
}

