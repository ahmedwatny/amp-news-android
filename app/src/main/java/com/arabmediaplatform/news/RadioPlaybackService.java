package com.arabmediaplatform.news;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class RadioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.arabmediaplatform.news.PLAY";
    public static final String ACTION_PAUSE = "com.arabmediaplatform.news.PAUSE";
    public static final String ACTION_RESUME = "com.arabmediaplatform.news.RESUME";
    public static final String ACTION_STOP = "com.arabmediaplatform.news.STOP";
    public static final String ACTION_VOLUME = "com.arabmediaplatform.news.VOLUME";
    public static final String ACTION_STATE_CHANGED = "com.arabmediaplatform.news.RADIO_STATE_CHANGED";
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_VOLUME = "volume";
    public static final String EXTRA_IS_PLAYING = "is_playing";

    private static final String CHANNEL_ID = "amp_radio_playback";
    private static final int NOTIFICATION_ID = 4102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private MediaSessionCompat mediaSession;
    private String streamUrl;
    private float volume = .8f;
    private boolean isPlaying = false;
    private boolean userStopped = false;
    private int retryCount = 0;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        mediaSession = new MediaSessionCompat(this, "AMPRadioSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onStop() { stopCompletely(); }
        });
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "إذاعة المنصة")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Arab Media Platform")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "بث مباشر")
                .build());
        mediaSession.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            android.content.SharedPreferences prefs = getSharedPreferences("amp_radio", MODE_PRIVATE);
            streamUrl = prefs.getString("stream_url", null);
            volume = prefs.getFloat("volume", .8f);
            if (streamUrl != null && prefs.getBoolean("was_playing", false)) {
                userStopped = false;
                startForeground(NOTIFICATION_ID, buildNotification());
                startPlayer();
                return START_STICKY;
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) { stopCompletely(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(action)) { pausePlayback(); return START_NOT_STICKY; }
        if (ACTION_RESUME.equals(action)) { resumePlayback(); return START_NOT_STICKY; }
        if (ACTION_VOLUME.equals(action)) {
            volume = intent.getFloatExtra(EXTRA_VOLUME, volume);
            getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putFloat("volume", volume).apply();
            if (player != null) { try { player.setVolume(volume, volume); } catch (Exception ignored) {} }
            else if (streamUrl == null) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) streamUrl = url;
            volume = intent.getFloatExtra(EXTRA_VOLUME, volume);
            if (streamUrl == null) { stopSelf(); return START_NOT_STICKY; }
            getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putString("stream_url", streamUrl).putFloat("volume", volume).putBoolean("was_playing", true).apply();
            userStopped = false;
            retryCount = 0;
            startForeground(NOTIFICATION_ID, buildNotification());
            startPlayer();
        }
        return START_NOT_STICKY;
    }

    private void startPlayer() {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setVolume(volume, volume);
            player.setOnPreparedListener(mp -> {
                if (userStopped) return;
                try {
                    mp.start();
                    isPlaying = true;
                    retryCount = 0;
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    updateNotification();
                    publishState(true);
                } catch (Exception e) { scheduleRetry(); }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                publishState(false);
                scheduleRetry();
                return true;
            });
            player.setDataSource(streamUrl);
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING);
            player.prepareAsync();
        } catch (Exception e) { scheduleRetry(); }
    }

    private void scheduleRetry() {
        releasePlayer();
        isPlaying = false;
        updateNotification();
        publishState(false);
        if (userStopped || streamUrl == null) return;
        if (retryCount >= 20) {
            getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putBoolean("was_playing", false).apply();
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            stopForeground(true);
            stopSelf();
            return;
        }
        retryCount++;
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, Math.min(15000, 2000L * retryCount));
    }
    private final Runnable retryRunnable = this::startPlayer;

    private void pausePlayback() {
        handler.removeCallbacks(retryRunnable);
        if (player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        }
        isPlaying = false;
        getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putBoolean("was_playing", false).apply();
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
        updateNotification();
        publishState(false);
    }

    private void resumePlayback() {
        if (streamUrl == null) return;
        userStopped = false;
        getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putBoolean("was_playing", true).putString("stream_url", streamUrl).putFloat("volume", volume).apply();
        if (player == null) {
            startForeground(NOTIFICATION_ID, buildNotification());
            startPlayer();
            return;
        }
        try {
            if (!player.isPlaying()) player.start();
            isPlaying = true;
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            updateNotification();
            publishState(true);
        } catch (Exception e) { startForeground(NOTIFICATION_ID, buildNotification()); startPlayer(); }
    }

    private void stopCompletely() {
        userStopped = true;
        getSharedPreferences("amp_radio", MODE_PRIVATE).edit().putBoolean("was_playing", false).apply();
        handler.removeCallbacks(retryRunnable);
        isPlaying = false;
        releasePlayer();
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
        publishState(false);
        stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.setOnPreparedListener(null); player.setOnErrorListener(null); player.reset(); player.release(); }
            catch (Exception ignored) {}
            player = null;
        }
    }

    private void updatePlaybackState(int state) {
        if (mediaSession == null) return;
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions).setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, isPlaying ? 1f : 0f)
                .build());
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent contentPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        boolean paused = !isPlaying;
        NotificationCompat.Action playPause = new NotificationCompat.Action(
                paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                paused ? "استئناف" : "إيقاف مؤقت", servicePending(paused ? ACTION_RESUME : ACTION_PAUSE, 1));
        NotificationCompat.Action stop = new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", servicePending(ACTION_STOP, 2));
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("إذاعة المنصة")
                .setContentText(isPlaying ? "Arab Media Platform · بث مباشر" : "الراديو متوقف مؤقتًا")
                .setContentIntent(contentPending)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(playPause)
                .addAction(stop)
                .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1))
                .build();
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent intent = new Intent(this, RadioPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private void publishState(boolean playing) {
        Intent state = new Intent(ACTION_STATE_CHANGED);
        state.setPackage(getPackageName());
        state.putExtra(EXTRA_IS_PLAYING, playing);
        sendBroadcast(state);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "راديو AMP", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("مشغل إذاعة المنصة والتحكم في البث بالخلفية");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Keep the foreground radio service alive when the app is removed from recents.
        super.onTaskRemoved(rootIntent);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); mediaSession = null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
