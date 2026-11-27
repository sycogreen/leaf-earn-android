
package com.leafearn.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService
        extends FirebaseMessagingService {

    private static final String TAG = "LeafEarnFCM";
    private static final String CHANNEL_ID = "leaf_earn_alerts";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        // Token refresh is detected here.
        // Uploading it to the Leaf Earn backend is a separate step.
        Log.d(TAG, "FCM registration token refreshed.");
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "Leaf Earn";
        String body = "";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }

            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (remoteMessage.getData().containsKey("title")) {
            title = remoteMessage.getData().get("title");
        }

        if (remoteMessage.getData().containsKey("body")) {
            body = remoteMessage.getData().get("body");
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if (manager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "Leaf Earn Alerts",
                                NotificationManager.IMPORTANCE_HIGH
                        );

                channel.setDescription(
                        "Important updates from Leaf Earn"
                );

                manager.createNotificationChannel(channel);
            }

            Intent intent =
                    new Intent(this, MainActivity.class);

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            int flags =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT;

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            (int) (System.currentTimeMillis() % 100000),
                            intent,
                            flags
                    );

            Notification.Builder builder;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            Notification notification =
                    builder
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setContentTitle(
                                    title != null ? title : "Leaf Earn"
                            )
                            .setContentText(
                                    body != null ? body : ""
                            )
                            .setStyle(
                                    new Notification.BigTextStyle()
                                            .bigText(body != null ? body : "")
                            )
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build();

            manager.notify(
                    (int) (System.currentTimeMillis() % 100000),
                    notification
            );

        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission not granted", e);

        } catch (Exception e) {
            Log.e(TAG, "Unable to show FCM notification", e);
        }
    }
          }
