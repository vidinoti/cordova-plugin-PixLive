package com.vidinoti.pixlive;

import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.vidinoti.android.vdarsdk.DeviceCameraImageSender;
import com.vidinoti.android.vdarsdk.VDARAnnotationView;
import com.vidinoti.android.vdarsdk.VDARCode;
import com.vidinoti.android.vdarsdk.VDARContext;
import com.vidinoti.android.vdarsdk.VDARSDKController;
import com.vidinoti.android.vdarsdk.VDARSDKControllerEventReceiver;
import com.vidinoti.android.vdarsdk.VDARRemoteController;
import com.vidinoti.android.vdarsdk.VDARRemoteControllerListener;

/**
 * This class echoes a string called from JavaScript.
 */
public class PixLive extends CordovaPlugin {

    private static final String TAG ="PixLiveCordova";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("init") && args.length()>=2) {
            String url = args.getString(0);
            String key = args.getString(1);
            this.init(url,key, callbackContext);
            return true;
        }
        return false;
    }

    private void init(String storageURL, String licenseKey, CallbackContext callbackContext) {

        String storage = null;
        try {
            storage = new URL(storageURL).getPath();
        } catch (MalformedURLException e) {
            VDARSDKController.log(Log.ERROR,TAG,"Invalid storage path for PixLive SDK: "+Log.getStackTraceString(e));
            return;
        }

        VDARSDKController.startSDK(cordova.getActivity(), storage,  licenseKey);
    }
}
