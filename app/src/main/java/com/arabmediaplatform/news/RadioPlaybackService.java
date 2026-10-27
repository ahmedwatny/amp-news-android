package com.arabmediaplatform.news;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;

public class RadioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.arabmediaplatform.news.PLAY";
    public static final String ACTION_STOP = "com.arabmediaplatform.news.STOP";
    public static final String ACTION_VOLUME = "com.arabmediaplatform.news.VOLUME";
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_VOLUME = "volume";
    private static final String CHANNEL_ID = "amp_radio_playback";
    private static final int NOTIFICATION_ID = 4102;
    private MediaPlayer player;

    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) { stopPlayback(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
        if (ACTION_VOLUME.equals(action)) { if (player != null) { float v = intent.getFloatExtra(EXTRA_VOLUME, .8f); player.setVolume(v, v); } return START_NOT_STICKY; }
        if (ACTION_PLAY.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            float volume = intent.getFloatExtra(EXTRA_VOLUME, .8f);
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) { stopSelf(); return START_NOT_STICKY; }
            startForeground(NOTIFICATION_ID, buildNotification());
            startPlayer(url, volume);
        }
        return START_NOT_STICKY;
    }

    private void startPlayer(String url, float volume) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setVolume(volume, volume);
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnErrorListener((mp, what, extra) -> { stopPlayback(); stopForeground(true); stopSelf(); return true; });
            player.prepareAsync();
        } catch (Exception e) { stopPlayback(); stopForeground(true); stopSelf(); }
    }

    private void stopPlayback() {
        if (player != null) { try { player.setOnPreparedListener(null); player.reset(); player.release(); } catch (Exception ignored) {} player = null; }
    }
    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Arab Media Platform")
            .setContentText("الراديو يعمل في الخلفية").setContentIntent(pending)
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_TRANSPORT).setPriority(NotificationCompat.PRIORITY_LOW).build();
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "راديو AMP", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("إشعار تشغيل الراديو في الخلفية");
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    @Override public void onDestroy() { stopPlayback(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
