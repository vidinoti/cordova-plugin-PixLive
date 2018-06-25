
package com.vidinoti.pixlive;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.vidinoti.android.vdarsdk.bookmark.BookmarkManager;
import com.vidinoti.android.vdarsdk.camera.DeviceCameraImageSender;
import com.vidinoti.android.vdarsdk.IBeaconSensor;
import android.support.v4.app.NotificationCompat;
import com.vidinoti.android.vdarsdk.NotificationFactory;
import com.vidinoti.android.vdarsdk.Sensor;
import com.vidinoti.android.vdarsdk.VDARAnnotationView;
import com.vidinoti.android.vdarsdk.VDARCode;
import com.vidinoti.android.vdarsdk.VDARCodeType;
import com.vidinoti.android.vdarsdk.VDARContext;
import com.vidinoti.android.vdarsdk.VDARIntersectionPrior;
import com.vidinoti.android.vdarsdk.VDARPrior;
import com.vidinoti.android.vdarsdk.VDARRemoteController;
import com.vidinoti.android.vdarsdk.VDARSDKController;
import com.vidinoti.android.vdarsdk.VDARRemoteControllerListener;
import com.vidinoti.android.vdarsdk.VDARSDKControllerEventReceiver;
import com.vidinoti.android.vdarsdk.VDARContentEventReceiver;
import com.vidinoti.android.vdarsdk.VDARSDKSensorEventReceiver;
import com.vidinoti.android.vdarsdk.VDARContextPrior;
import com.vidinoti.android.vdarsdk.VDARTagPrior;
import com.vidinoti.android.vdarsdk.VDARTourPrior;
import com.vidinoti.android.vdarsdk.VidiBeaconSensor;
import com.vidinoti.android.vdarsdk.geopoint.VDARGPSPoint;
import com.vidinoti.android.vdarsdk.geopoint.GeoPointManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * This class echoes a string called from JavaScript.
 */
public class PixLive extends CordovaPlugin implements VDARSDKControllerEventReceiver,VDARContentEventReceiver, VDARSDKSensorEventReceiver, VDARRemoteControllerListener {

    private static final String TAG ="PixLiveCordova";

    private static final int REQ_CODE_EXTERNAL_STORAGE_PERM = 100;

    private boolean locEnabled = false;
    private CallbackContext mCaptureScreenshotCallbackContext = null;

    class TouchInterceptorView extends FrameLayout {
        public boolean touchEnabled = true;

        private View.OnTouchListener privateListener = null;
        private final float mDensity;

        private float mHoleTop = 0;
        private float mHoleBottom = 0;
        private float mHoleLeft = 0;
        private float mHoleRight = 0;


        public TouchInterceptorView(android.content.Context context) {
            super(context);
            mDensity = context.getResources().getDisplayMetrics().density;
        }

        public void setTouchHole(int top, int bottom, int left, int right) {
            // Convert the pixels into screen coordinates using the screen density
            this.mHoleTop = top * mDensity;
            this.mHoleBottom = bottom * mDensity;
            this.mHoleLeft = left * mDensity;
            this.mHoleRight = right * mDensity;
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

        @Override
        public boolean onInterceptTouchEvent (MotionEvent ev) {

            if((!touchEnabled || arViews.size()==0)) {
                return super.onInterceptTouchEvent(ev);
            }

            int thisViewLeft = getRelativeLeft(this);
            int thisViewTop = getTop();
            float x = ev.getX() - thisViewLeft;
            float y = ev.getY() - thisViewTop;

            // If the position corresponds to the "hole", we do not intercept the event.
            if (x >= this.mHoleLeft && x < this.mHoleRight && y >= this.mHoleTop && y < this.mHoleBottom) {
                return super.onInterceptTouchEvent(ev);
            }

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
        VDARSDKController.getInstance().registerContentEventReceiver(this);
        VDARSDKController.getInstance().setActivity(cordova.getActivity());
        VDARSDKController.getInstance().registerEventReceiver(this);

        VDARSDKController.getInstance().registerSensorEventReceiver(this);

        VDARSDKController.getInstance().addNewAfterLoadingTask(new Runnable() {
            @Override
            public void run() {

                VDARRemoteController.getInstance().addProgressListener(PixLive.this);

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

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        // Handle the result for external storage permission or forward to SDK
        if (requestCode == REQ_CODE_EXTERNAL_STORAGE_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveTempScreenshot();
            } else if (mCaptureScreenshotCallbackContext != null) {
                mCaptureScreenshotCallbackContext.error("Permission not granted");
            }
            mCaptureScreenshotCallbackContext = null;
        } else {
            VDARSDKController.getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
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
            JSONArray tagArray = null;
            if (args.length() > 0) {
                tagArray = args.getJSONArray(0);
            }
            JSONArray tourArray = null;
            if (args.length() > 1) {
                tourArray = args.getJSONArray(1);
            }
            JSONArray contextArray = null;
            if (args.length() > 2) {
                contextArray = args.getJSONArray(2);
            }
            this.synchronize(tagArray, tourArray, contextArray, callbackContext);
            return true;
        } else if (action.equals("enableTouch")) {
            this.enableTouch();
            return true;
        } else if (action.equals("disableTouch")) {
            this.disableTouch();
            return true;
        } else if (action.equals("setTouchHole")) {
            this.setTouchHole(args.getInt(0), args.getInt(1), args.getInt(2), args.getInt(3));
        } else if (action.equals("isContainingGPSPoints")) {
            this.isContainingGPSPoints(callbackContext);
            return true;
        } else if (action.equals("computeDistanceBetweenGPSPoints") && args.length()==4) {
            this.computeDistanceBetweenGPSPoints((float)(args.getDouble(0)), (float)(args.getDouble(1)), (float)(args.getDouble(2)), (float)(args.getDouble(3)), callbackContext);
            return true;
        } else if (action.equals("getNearbyBeacons")) {
            this.getNearbyBeacons(callbackContext);
            return true;
        } else if (action.equals("setEnableNearbyRequirementDialog")) {
            this.setEnableNearbyRequirementDialog(args.getBoolean(0));
            return true;
        } else if (action.equals("getNearbyStatus")) {
            this.getNearbyStatus(callbackContext);
            return true;
        } else if (action.equals("isContainingBeacons")) {
            this.isContainingBeacons(callbackContext);
            return true;
        } else if (action.equals("getNearbyGPSPoints") && args.length()==2) {
            this.getNearbyGPSPoints((float)(args.getDouble(0)), (float)(args.getDouble(1)),callbackContext);
            return true;
        } else if (action.equals("getGPSPointsInBoundingBox") && args.length()==4) {
            this.getGPSPointsInBoundingBox((float)(args.getDouble(0)), (float)(args.getDouble(1)), (float)(args.getDouble(2)), (float)(args.getDouble(3)), callbackContext);
            return true;
        } else if (action.equals("setNotificationsSupport") && args.length()>=1) {
            // Backward compatibility with previous version, was expecting a String (Sender ID), now we expect a boolean.
            boolean enableNotif = args.optBoolean(0) || !args.optString(0).equals("false");
            this.setNotificationsSupport(enableNotif);
            return true;
        } else if (action.equals("installEventHandler")) {
            this.installEventHandler(callbackContext);
            return true;
        } else if (action.equals("presentNotificationsList")) {
            this.presentNotificationsList(callbackContext);
            return true;
        } else if (action.equals("presentNearbyList")) {
            if (args.length() == 2) {
                this.presentNearbyList((float) args.getDouble(0), (float) args.getDouble(1));
                return true;
            } else if(args.length() == 0) {
                this.presentNearbyList();
                return true;
            } else {
                return false;
            }
        } else if (action.equals("refreshNearbyList") && args.length() >= 2) {
            this.refreshNearbyList((float) args.getDouble(0), (float) args.getDouble(1));
        } else if (action.equals("openURLInInternalBrowser") && args.length()>=1) {
            this.openURLInInternalBrowser(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("getContexts")) {
            this.getContexts(callbackContext);
            return true;
        } else if (action.equals("getContext") && args.length()>=1) {
            this.getContext(args.getString(0),callbackContext);
            return true;
        } else if (action.equals("activateContext") && args.length()>=1) {
            this.activateContext(args.getString(0),callbackContext);
            return true;
        } else if (action.equals("ignoreContext") && args.length()>=1) {
            this.ignoreContext(args.getString(0),callbackContext);
            return true;
        } else if (action.equals("setLocalizationEnabled")) {
            locEnabled = true;
            VDARSDKController.getInstance().getLocalizationManager().startLocalization();
            return true;
        } else if (action.equals("pageLoaded")) {
            if(activityActive) {
                for(Runnable r : foregroundCallbacks) {
                    r.run();
                }

                foregroundCallbacks.clear();
            }
            pageLoaded=true;
            return true;
        } else if (action.equals("setBookmarkSupport") && args.length()>=1) {
            this.setBookmarkSupport(args.getBoolean(0));
            return true;
        } else if (action.equals("getBookmarks")) {
            this.getBookmarks(callbackContext);
            return true;
        } else if (action.equals("addBookmark") && args.length()>=1) {
            this.addBookmark(args.getString(0));
            return true;
        } else if (action.equals("removeBookmark") && args.length()>=1) {
            this.removeBookmark(args.getString(0));
            return true;
        } else if (action.equals("isBookmarked") && args.length()>=1) {
            this.isBookmarked(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("setCloudRecognitionLanguage") && args.length()>=1) {
            this.setCloudRecognitionLanguage(args.getString(0));
            return true;
        } else if (action.equals("captureScreenshot")) {
            this.captureScreenshot(args.getInt(0), callbackContext);
            return true;
        }
        return false;
    }

    private JSONObject createJSONForGPSPoint(VDARGPSPoint gpsPoint) {
        JSONObject obj = new JSONObject();

        SimpleDateFormat format = new SimpleDateFormat("Z");

        try {
            obj.put("contextId", gpsPoint.getContextID());
            obj.put("label", gpsPoint.getLabel());
            obj.put("category", gpsPoint.getCategory());
            obj.put("lat", gpsPoint.getLat());
            obj.put("lon", gpsPoint.getLon());
            obj.put("detectionRadius", gpsPoint.getDetectionRadius());
            obj.put("distanceFromCurrentPos", gpsPoint.getDistanceFromCurrentPos());
        } catch (JSONException e) {
        }

        return obj;
    }

    private void isContainingGPSPoints(final CallbackContext callback) {
        boolean containGPSPoints = GeoPointManager.isContainingGPSPoints();
        callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, containGPSPoints));
    }

    private void computeDistanceBetweenGPSPoints(float lat1, float lon1, float lat2, float lon2, final CallbackContext callback) {
        float distance = GeoPointManager.computeDistanceBetweenGPSPoints(lat1, lon1, lat2, lon2);
        callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, distance));
    }

    private void getNearbyBeacons(final CallbackContext callbackContext) {
        final List<String> contextIds = VDARSDKController.getInstance().getNearbyBeacons();
        JSONArray ret = new JSONArray();

        for(String ctxId : contextIds) {
            VDARContext c = VDARSDKController.getInstance().getContext(ctxId);
            if(c != null) {
                ret.put(createJSONForContext(c));
            }
        }
        if(!isWebViewDestroyed()) {
            callbackContext.success(ret);
        }
    }

    private void isContainingBeacons(final CallbackContext callback) {
        boolean containingBeacons = VDARSDKController.getInstance().isContainingBeacons();
        callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, containingBeacons));
    }

    private void setEnableNearbyRequirementDialog(boolean nearbyRequirementDialogEnabled) {
        VDARSDKController.getInstance().setEnableNearbyRequirementDialog(nearbyRequirementDialogEnabled);
    }

    private void getNearbyStatus(final CallbackContext callback) {
        HashMap<String, String> status = VDARSDKController.getInstance().getNearbyStatus();

        JSONObject obj = new JSONObject();

        String authorizationStatus = "enabled";
        String locationStatus = "enabled";
        String bluetoothStatus = "enabled";


        if(status.get("ACCESS_FINE_LOCATION") != "enabled") {
            authorizationStatus = "disabled";
        }

        if(status.get("GPS_PROVIDER") != "enabled" && status.get("NETWORK_PROVIDER") != "enabled") {
            locationStatus = "disabled";
        }

        if(status.get("bluetoothStatus") != "enabled") {
            bluetoothStatus = "disabled";
        }

        try {
            obj.put("authorizationStatus",authorizationStatus);
            obj.put("locationStatus",locationStatus);
            obj.put("bluetoothStatus",bluetoothStatus);
        } catch (JSONException e) {

        }

        callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, obj));
    }

    private void getNearbyGPSPoints(float myLat, float myLon, final CallbackContext callback) {
        final List<VDARGPSPoint> gpsPoints = GeoPointManager.getNearbyGPSPoints(myLat, myLon);
        JSONArray ret = new JSONArray();
        for(VDARGPSPoint gpsPoint : gpsPoints) {
            ret.put(createJSONForGPSPoint(gpsPoint));
        }
        if(!isWebViewDestroyed()) {
            callback.success(ret);
        }
    }

    private void getGPSPointsInBoundingBox(float minLat, float minLon, float maxLat, float maxLon, final CallbackContext callback) {
        final List<VDARGPSPoint> gpsPoints = GeoPointManager.getGPSPointsInBoundingBox(minLat, minLon, maxLat, maxLon);
        JSONArray ret = new JSONArray();
        for(VDARGPSPoint gpsPoint : gpsPoints) {
            ret.put(createJSONForGPSPoint(gpsPoint));
        }
        if(!isWebViewDestroyed()) {
            callback.success(ret);
        }
    }

    private void setBookmarkSupport(boolean enabled) {
        VDARSDKController.getInstance().setBookmarkSupport(enabled);
    }

    private void getBookmarks(final CallbackContext callback) {
        final List<String> contextIds = BookmarkManager.getBookmarks();
        JSONArray ret = new JSONArray();
        for(String ctxId : contextIds) {
            VDARContext c = VDARSDKController.getInstance().getContext(ctxId);
            if(c != null) {
                ret.put(createJSONForContext(c));
            }
        }
        if(!isWebViewDestroyed()) {
            callback.success(ret);
        }
    }

    private void addBookmark(String contextId) {
        BookmarkManager.addBookmark(contextId);
    }

    private void removeBookmark(String contextId) {
        BookmarkManager.removeBookmark(contextId);
    }

    private void isBookmarked(String contextId, final CallbackContext callbackContext) {
        boolean bookmarked = BookmarkManager.isBookmarked(contextId);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, bookmarked));
    }

    private void setCloudRecognitionLanguage(String languageCode) {
        VDARSDKController.getInstance().setCloudRecognitionLanguage(languageCode);
    }

    private void installEventHandler(CallbackContext callback) {
        this.eventHandler = callback;
    }

    private void setNotificationsSupport(boolean enabled) {
        VDARSDKController.getInstance().setNotificationsSupport(enabled);
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
     * Defines a region where the touch events are not intercepted by the plugin.
     * This is required if an element is put on top of the ar view (e.g. a button).
     * @param top the top of the region in px
     * @param bottom the bottom of the region in px
     * @param left the left of the region in px
     * @param right the right of the region in px
     */
    private void setTouchHole(int top, int bottom, int left, int right) {
        if (touchView != null) {
            touchView.setTouchHole(top, bottom, left, right);
        }
    }

    /**
     * Creates a screen capture and saves it in a public folder accessible for the
     * image gallery.
     * @param ctrlID the ID of the current AR view
     * @param callbackContext the cordova CallbackContext for returning the result of the action
     */
    private void captureScreenshot(int ctrlID, final CallbackContext callbackContext) {

        VDARAnnotationView view = arViews.get(ctrlID);
        if (view == null) {
            callbackContext.error("No AR view");
            return;
        }

        if (cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            final File file = getExternalScreenshotFile();
            view.captureScreenshot(file.getAbsolutePath(), new Observer() {
                @Override
                public void update(Observable o, Object arg) {
                    if (arg == null) {
                        // Force a media scan for adding the image in the gallery
                        MediaScannerConnection.scanFile(cordova.getActivity(), new String[]{file.toString()}, null, null);
                        callbackContext.success();
                    } else {
                        callbackContext.error("Error capturing screen");
                    }
                }
            });
        } else {
            // Store the callbackContext for calling it later in onRequestPermissionResult
            mCaptureScreenshotCallbackContext = callbackContext;
            // We first store the capture internally (private to the app)
            // Once the permission has been granted, we move it to a public folder
            File file = new File(cordova.getActivity().getCacheDir(), "capture_temp_file.jpg");
            view.captureScreenshot(file.getAbsolutePath(), new Observer() {
                @Override
                public void update(Observable o, Object arg) {
                    if (arg == null) {
                        cordova.requestPermission(PixLive.this, REQ_CODE_EXTERNAL_STORAGE_PERM, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    } else {
                        callbackContext.error("Error capturing screen");
                        mCaptureScreenshotCallbackContext = null;
                    }
                }
            });
        }
    }

    /**
     * Moves the temporary screen capture from temporary private folder to public folder.
     */
    public void saveTempScreenshot() {
        File srcFile = new File(cordova.getActivity().getCacheDir(), "capture_temp_file.jpg");
        File destFile = getExternalScreenshotFile();
        final boolean rename = srcFile.renameTo(destFile);
        if (!rename) {
            try {
                copyFile(srcFile, destFile);
            } catch (IOException e) {
                if (mCaptureScreenshotCallbackContext != null) {
                  mCaptureScreenshotCallbackContext.error("Error copying file");
                }
                return;
            }
            srcFile.delete();
        }
        MediaScannerConnection.scanFile(cordova.getActivity(), new String[]{destFile.toString()}, null, null);
        if (mCaptureScreenshotCallbackContext != null) {
          mCaptureScreenshotCallbackContext.success();
        }
    }

    /**
     * Returns a file that is public
     */
    private File getExternalScreenshotFile() {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        path.mkdirs();
        return new File(path, "ar_" + timestamp + ".jpg");
    }

    /**
     * Copy a file from a source to a destination.
     * @param srcFile the file to copy
     * @param destFile the destination of the file to copy
     */
    private void copyFile(File srcFile, File destFile) throws IOException {
        FileChannel input = null;
        FileChannel output = null;
        try {
            FileInputStream fis = new FileInputStream(srcFile);
            input = fis.getChannel();
            FileOutputStream fos = new FileOutputStream(destFile);
            output = fos.getChannel();
            final long size = input.size();
            long pos = 0;
            long count = 0;
            while (pos < size) {
                final long remain = size - pos;
                count = remain > 1048576 ? 1048576 : remain;
                final long bytesCopied = output.transferFrom(input, pos, count);
                if (bytesCopied == 0) {
                    break;
                }
                pos += bytesCopied;
            }
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
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

        VDARSDKController.getInstance().unregisterEventReceiver(this);
        VDARSDKController.getInstance().unregisterContentEventReceiver(this);
        VDARSDKController.getInstance().unregisterSensorEventReceiver(this);
        VDARRemoteController.getInstance().removeProgressListener(this);

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

        if(locEnabled)
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

    private void presentNearbyList() {
        VDARSDKController.getInstance().presentNearbyList();
    }

    private void presentNearbyList(final float latitude, final float longitude) {
        VDARSDKController.getInstance().presentNearbyList(latitude, longitude);
    }

    private void refreshNearbyList(final float latitude, final float longitude) {
        VDARSDKController.getInstance().refreshNearbyList(latitude, longitude);
    }

    private void openURLInInternalBrowser(String url, final CallbackContext callbackContext) {
        try {
            URL urlObj = new URL(url);

            VDARSDKController.getInstance().openURLInInternalBrowser(urlObj);
        } catch(MalformedURLException e) {

        }
    }

    private ArrayList<VDARPrior> getPriorsFromJSON(JSONArray a) {
        ArrayList<VDARPrior> ret = new ArrayList<VDARPrior>();

        if(a != null) {
            for(int i = 0; i < a.length(); i++) {
                Object s = null;
                try {
                    s = a.get(i);
                } catch (JSONException e) {
                    continue;
                }

                if(s instanceof String) {
                    ret.add(new VDARTagPrior((String)s));
                } else if(s instanceof JSONArray) {
                    ArrayList<VDARPrior> ret2 = getPriorsFromJSON((JSONArray)s);

                    ret.add(new VDARIntersectionPrior(ret2));
                }
            }
        }

        return ret;
    }

    private void synchronize(JSONArray tags, JSONArray tours, JSONArray contexts, final CallbackContext callbackContext) {

        final ArrayList<VDARPrior> priors = getPriorsFromJSON(tags);

        if (tours != null) {
            for (int i = 0; i < tours.length(); i++) {
                try {
                    long tourId = tours.getLong(i);
                    priors.add(new VDARTourPrior(tourId));
                } catch (JSONException e) {
                    Log.w(TAG, "Unable to parse sync tour ID.");
                }
            }
        }

        if (contexts != null) {
            for (int i = 0; i < contexts.length(); i++) {
                try {
                    String contextID = contexts.getString(i);
                    priors.add(new VDARContextPrior(contextID));
                } catch (JSONException e) {
                    Log.w(TAG, "Unable to parse context ID.");
                }
            }
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
                                    try {
                                        callbackContext.success(ctx);
                                    } catch(Exception e) {}
                                }
                            } else {
                                if(!isWebViewDestroyed()) {
                                    try {
                                        callbackContext.error(info.getError());
                                    } catch(Exception e) {}
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

    private JSONObject createJSONForContext(VDARContext c) {
        JSONObject obj = new JSONObject();

        SimpleDateFormat format = new SimpleDateFormat("Z");

        try {
            obj.put("contextId",c.getRemoteID());
            obj.put("name",c.getName());
            obj.put("lastUpdate",format.format(c.getLastModifiedDate()));
            obj.put("description",c.getDescription());
            obj.put("notificationTitle",c.getNotificationTitle());
            obj.put("notificationMessage",c.getNotificationMessage());
            obj.put("imageThumbnailURL",c.getImageThumbnailURL());
            obj.put("imageHiResURL",c.getImageHiResURL());
        } catch (JSONException e) {

        }

        return obj;
    }

    private void getContexts(final CallbackContext callbackContext) {
        final ArrayList<String> contextIds = VDARSDKController.getInstance().getAllContextIDs();
        JSONArray ret = new JSONArray();

        for(String ctxId : contextIds) {
            VDARContext c = VDARSDKController.getInstance().getContext(ctxId);
            if(c != null) {


                ret.put(createJSONForContext(c));
            }
        }
        if(!isWebViewDestroyed()) {
            callbackContext.success(ret);
        }
    }

    private void getContext(final String ctxId, final CallbackContext callbackContext) {
        VDARContext c = VDARSDKController.getInstance().getContext(ctxId);
        if(c != null) {
            JSONObject o = createJSONForContext(c);
            if(!isWebViewDestroyed()) {
                callbackContext.success(o);
            }
        } else {
            if(!isWebViewDestroyed()) {
                callbackContext.error("Invalid contextId");
            }
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

                    if (Build.VERSION.SDK_INT >= 23) {
                        //FIXME: This is a hack, we enfore the cordova impl to have our own plugin as requestPermission callback

                        try {
                            Field fs = cordova.getClass().getDeclaredField("permissionResultCallback");
                            fs.setAccessible(true);
                            fs.set(cordova, PixLive.this);
                        } catch(Exception e) {

                        }
                    }
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

    @Override
    public void onCodesRecognized(ArrayList<com.vidinoti.android.vdarsdk.VDARCode> arrayList) {
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

                    try {
                        PixLive.this.eventHandler.sendPluginResult(p);
                    } catch (Exception e) {
                        //To avoid webview crashes
                    }
                }
            }
        }
    }

    @Override
    public void onFatalError(String s) {

    }

    @Override
    public void onPresentAnnotations() {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "presentAnnotations");
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
    public void onAnnotationsHidden() {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "hideAnnotations");
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
    public void onTrackingStarted(int w, int h) {

    }

    @Override
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

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
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

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
    public void onReceiveContentEvent(final String eventName, final String eventParams) {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "eventFromContent");
                o.put("eventName", eventName);
                o.put("eventParams", eventParams);
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
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

                    try {
                        PixLive.this.eventHandler.sendPluginResult(p);
                    } catch (Exception e) {
                        //To avoid webview crashes
                    }
                }
            }
        };


        if(activityActive && pageLoaded) {
            r.run();
        } else {
            foregroundCallbacks.add(r);
        }

    }

    @Override
    public void onSensorTriggered(Sensor sensor, VDARContext context) {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", sensor.isTriggered() ? "sensorTriggered" : "sensorUntriggered");
                o.put("sensorId", sensor.getSensorId());
                o.put("sensorType", sensor.getType());
                o.put("context", createJSONForContext(context));

                if(sensor.isTriggered()) {
                    if(sensor instanceof VidiBeaconSensor) {
                        o.put("rssi",((VidiBeaconSensor)sensor).getRssi());
                    } else if(sensor instanceof IBeaconSensor) {
                        o.put("rssi",((IBeaconSensor)sensor).getRssi());
                        o.put("distance",((IBeaconSensor)sensor).getDistance());
                    }
                }

            } catch (JSONException e) {

            }


            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
    public void onSensorUpdated(Sensor sensor, VDARContext context) {
        if(this.eventHandler != null) {

            JSONObject o = new JSONObject();

            try {
                o.put("type", "sensorUpdate");
                o.put("sensorId", sensor.getSensorId());
                o.put("sensorType", sensor.getType());
                o.put("context", createJSONForContext(context));

                if(sensor instanceof VidiBeaconSensor) {
                    o.put("rssi",((VidiBeaconSensor)sensor).getRssi());
                } else if(sensor instanceof IBeaconSensor) {
                    o.put("rssi",((IBeaconSensor)sensor).getRssi());
                    o.put("distance",((IBeaconSensor)sensor).getDistance());
                }
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);
            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }

    @Override
    public void onSyncProgress(VDARRemoteController controller, float progress,
                               boolean isReady, String folder) {
        if(this.eventHandler != null) {
            JSONObject o = new JSONObject();

            try {
                o.put("type", "syncProgress");
                o.put("progress", progress / 100.0f);
            } catch (JSONException e) {

            }

            PluginResult p = new PluginResult(PluginResult.Status.OK, o);

            p.setKeepCallback(true);

            try {
                PixLive.this.eventHandler.sendPluginResult(p);
            } catch (Exception e) {
                //To avoid webview crashes
            }
        }
    }
}

