package com.vidinoti.pixlive;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.gcm.GcmListenerService;
import com.ionicframework.test2194887.R;

/**
 * Service used for receiving GCM messages. When a message is received this service will log it.
 */
public class GcmService extends GcmListenerService {

    @Override
    public void onMessageReceived(String from, Bundle data) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String nid = data.getString("nid");
        String message = data.getString("message");

        if(nid==null || nid.length()==0 || message==null || message.length()==0) {
            return;
        }

        Intent appIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());

        appIntent.putExtra("nid", nid).putExtra("remote",true);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_ONE_SHOT);

        int stringId = getApplicationInfo().labelRes;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                this).setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(stringId))
                .setContentText(message).setContentIntent(contentIntent).setAutoCancel(true);

        //For setting the light on
        mBuilder.setLights(0xFF23E223, 200, 100).setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setSound(RingtoneManager
                .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        int when = (int)((System.currentTimeMillis()-1419120000000L)/1000L);

        mNotificationManager.notify(when, mBuilder.build());
    }
}