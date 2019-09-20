/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.RemoteViews;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioService extends Service {
    public static final String ACTION_START_AUDIO = "AudioService.START";
    public static final String ACTION_STOP_AUDIO = "AudioService.STOP";
    public static final String ACTION_PAUSE_AUDIO = "AudioService.PAUSE";
    public static final String ACTION_STATUS_AUDIO = "AudioService.STATUS";
    public static final String ACTION_CONTROL_AUDIO = "AudioService.CONTROL";
    public static final String ACTION_NEXT_AUDIO = "AudioService.NEXT";
    public static final String ACTION_PREVIOUS_AUDIO = "AudioService.PREVIOUS";
    public static final String ACTION_DESTROY_SERVICE = "AudioService.DESTROY";

    public static final String AUDIO_NOTIFICATION_CHANNEL_ID = "AUDIO_NOTIFICATION_CHANNEL_ID";
    public static final String AUDIO_NOTIFICATION_ICON_RES = "AUDIO_NOTIFICATION_ICON_RES";
    public static final String AUDIO_NOTIFICATION_SHOW_CLOSE = "AUDIO_NOTIFICATION_SHOW_CLOSE";
    public static final String AUDIO_NOTIFICATION_MINIFIED = "AUDIO_NOTIFICATION_MINIFIED";

    public static final int AUDIO_SERVICE_NOTIFICATION = 4;

    public static final int AUDIO_PREPARED = 0;
    public static final int AUDIO_STARTED = 1;
    public static final int AUDIO_PAUSED = 2;
    public static final int AUDIO_STOPPED = 3;
    public static final int AUDIO_PROGRESS_UPDATED = 4;
    public static final int AUDIO_COMPLETED = 5;
    public static final int AUDIO_TRACK_CHANGED = 6;
    public static final int AUDIO_SERVICE_STARTED = 7;
    public static final int AUDIO_SERVICE_STOPPED = 8;

    public static boolean SERVICE_RUNNING = false;

    private Thread mUiThread;
    private long mProgressDelay = 1000;

    private MediaPlayer mMediaPlayer;
    private boolean mIsPrepared = false;
    private int mAttachedTag = Integer.MIN_VALUE;

    private ArrayList<Object> mTracks;
    private Object mCurrentSource;
    private int mCurrentTrack = 0;
    private boolean mWasPlaying;

    private boolean mLoop = false;

    private AudioServiceBinder mBinder = new AudioServiceBinder();
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private RemoteViews mContentView, mContentViewMin;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class AudioServiceBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaPlayer();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        SERVICE_RUNNING = true;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        broadcast(AUDIO_STOPPED);
        release();
    }

    @Override
    public void onDestroy() {
        broadcast(AUDIO_STOPPED);
        broadcast(AUDIO_SERVICE_STOPPED);
        release();
        stopSelf();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = "";
        if (intent != null && intent.getAction() != null)
            action = intent.getAction();

        switch (action) {
            case ACTION_NEXT_AUDIO:
                nextTrack();
                break;
            case ACTION_PREVIOUS_AUDIO:
                previousTrack();
                break;
            case ACTION_CONTROL_AUDIO:
                controlAudio();
                break;
            case ACTION_START_AUDIO:
                start();
                break;
            case ACTION_PAUSE_AUDIO:
                pause();
                break;
            case ACTION_STOP_AUDIO:
                stop();
                break;
            case ACTION_DESTROY_SERVICE:
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            default:
                int id = 1;
                int icon = R.drawable.thumb;
                boolean showClose = true;
                boolean minified = false;
                if (intent != null) {
                    id = intent.getIntExtra(AUDIO_NOTIFICATION_CHANNEL_ID, id);
                    icon = intent.getIntExtra(AUDIO_NOTIFICATION_ICON_RES, icon);
                    showClose = intent.getBooleanExtra(AUDIO_NOTIFICATION_SHOW_CLOSE, true);
                    minified = intent.getBooleanExtra(AUDIO_NOTIFICATION_MINIFIED, false);
                    mAttachedTag = intent.getIntExtra("tag", Integer.MIN_VALUE);
                }
                addNotification(id, icon, showClose, minified);
                broadcast(AUDIO_SERVICE_STARTED);
                mAttachedTag = Integer.MIN_VALUE;
                break;
        }
        return START_STICKY;
    }

    private PendingIntent getPendingIntent(int code, Intent intent) {
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            pendingIntent = PendingIntent.getForegroundService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        else
            pendingIntent = PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    private void addNotification(int channelId, int icon, boolean showClose, boolean minified) {
        mContentView = new RemoteViews(getPackageName(), R.layout.audio_notification);
        mContentViewMin = new RemoteViews(getPackageName(), R.layout.audio_notification_minified);
//        mContentView.setTextViewText(R.id.artist, getString(R.string.no_artist));
        mContentView.setTextViewText(R.id.title, getString(R.string.no_title));
        mContentViewMin.setTextViewText(R.id.title, getString(R.string.no_title));

        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(ACTION_CONTROL_AUDIO);
        PendingIntent pendingIntent = getPendingIntent(94, intent);
        mContentView.setOnClickPendingIntent(R.id.play, pendingIntent);

        if (!minified) {
            intent.setAction(ACTION_PREVIOUS_AUDIO);
            pendingIntent = getPendingIntent(73, intent);
            mContentView.setOnClickPendingIntent(R.id.rewind, pendingIntent);
            intent.setAction(ACTION_NEXT_AUDIO);
            pendingIntent = getPendingIntent(68, intent);
            mContentView.setOnClickPendingIntent(R.id.forward, pendingIntent);
        } else {
            mContentView.setViewVisibility(R.id.title, View.GONE);
            mContentView.setViewVisibility(R.id.rewind, View.GONE);
            mContentView.setViewVisibility(R.id.forward, View.GONE);
        }

        if (showClose) {
            intent.setAction(ACTION_DESTROY_SERVICE);
            pendingIntent = getPendingIntent(34, intent);
            mContentView.setOnClickPendingIntent(R.id.close, pendingIntent);
        } else
            mContentView.setViewVisibility(R.id.close, View.GONE);

        mBuilder = createBuilder(this, channelId)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(icon)
                .setContent(mContentView)
                .setCustomContentView(mContentViewMin)
                .setCustomBigContentView(mContentView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle()) // TODO androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle
                // https://developer.android.com/reference/androidx/media/app/NotificationCompat.DecoratedMediaCustomViewStyle.html
                .setWhen(System.currentTimeMillis());

        startForeground(AUDIO_SERVICE_NOTIFICATION, mBuilder.build());
    }

    @SuppressWarnings("deprecation")
    private NotificationCompat.Builder createBuilder(Context context, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return createBuilderO(context, id);
        else
            return new NotificationCompat.Builder(context);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private NotificationCompat.Builder createBuilderO(Context context, int id) {
        String channelId = BuildConfig.APPLICATION_ID + "_" + id;
        String channelName = context.getString(R.string.audio_channel);
        NotificationChannel chanel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        chanel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(chanel);
        return new NotificationCompat.Builder(context, channelId);
    }

    private void initMediaPlayer() {
        mTracks = new ArrayList<>();
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (!mIsPrepared)
                    return;
                if (isCorrectTrack(mCurrentTrack + 1)) {
                    mCurrentTrack++;
                    selectTrack(true);
                } else {
                    if (!mLoop) {
                        broadcast(AUDIO_COMPLETED);
                        setDataSource(mCurrentSource);
                        return;
                    }

                    if (isCorrectTrack(0)) {
                        mCurrentTrack = 0;
                        selectTrack(true);
                    } else {
                        pause();
                        broadcast(AUDIO_TRACK_CHANGED);
                        start();
                    }
                }
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mIsPrepared = true;
                int duration = mp.getDuration();
                if (duration > 0) {
                    mProgressDelay = mp.getDuration() / 100;
                    if (mProgressDelay < 1000) {
                        if (mProgressDelay < 100)
                            mProgressDelay = 100;
                    } else
                        mProgressDelay = 1000;
                }

                if (mWasPlaying) {
                    mp.start();
                    mWasPlaying = false;
                }

                mContentView.setTextViewText(R.id.title, getTrackTitle());
                mContentViewMin.setTextViewText(R.id.title, getTrackTitle());
                mNotificationManager.notify(AUDIO_SERVICE_NOTIFICATION, mBuilder.build());
                broadcast(AUDIO_PREPARED);
            }
        });
    }

    private void startUpdateThread() {
        if (mUiThread == null || mUiThread.isInterrupted() || mUiThread.isAlive() || mUiThread.getState() != Thread.State.NEW) {
            mUiThread = new Thread() {
                @Override
                public void run() {
                    while (!isInterrupted()) {
                        try {
                            Thread.sleep(mProgressDelay);
                            if (mIsPrepared && isPlaying())
                                broadcast(AUDIO_PROGRESS_UPDATED);
                        } catch (InterruptedException | IllegalStateException ignored) {
                        }
                    }
                }
            };
        }

        try {
            mUiThread.start();
        } catch (IllegalThreadStateException ignored) {
        }
    }

    public int getAttachedTag() {
        return mAttachedTag;
    }

    public void attachTag(int tag) {
//        Log.d("AudioView", "attaching " + tag);
        stop();
        mAttachedTag = tag;
    }

    private void broadcast(int type) {
//        Log.d("AudioView", "broadcast: " + type + " tag: " + mAttachedTag);
        Intent broadcast = new Intent(ACTION_STATUS_AUDIO);
        broadcast.putExtra("status", type);
        broadcast.putExtra("tag", mAttachedTag);
        sendBroadcast(broadcast);
    }

    public boolean isPrepared() {
        return mIsPrepared;
    }

    public boolean isPlaying() {
        try {
            return mMediaPlayer.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            return mMediaPlayer.getCurrentPosition();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public int getTotalDuration() {
        try {
            return mMediaPlayer.getDuration();
        } catch (IllegalStateException | NullPointerException ignored) {
            return 0;
        }
    }

    public void controlAudio() {
        if (mIsPrepared && isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    public void previousTrack() {
        if (isCorrectTrack(mCurrentTrack - 1))
            mCurrentTrack--;
        else
            return;

        selectTrack();
    }

    public void nextTrack() {
        if (isCorrectTrack(mCurrentTrack + 1))
            mCurrentTrack++;
        else
            return;

        selectTrack();
    }

    private boolean isCorrectTrack(int trackPosition) {
        return mTracks.size() > 0 && trackPosition >= 0 && trackPosition < mTracks.size();
    }

    private void selectTrack() {
        boolean wasPlaying = isPlaying();
        selectTrack(wasPlaying);
    }

    private void selectTrack(boolean wasPlaying) {
        if (mTracks.size() < 1)
            return;

        mWasPlaying = wasPlaying;
        Object track = mTracks.get(mCurrentTrack);

        try {
            if (track.getClass() == String.class) {
                setDataSource((String) track);
            } else if (track.getClass() == Uri.class) {
                setDataSource((Uri) track);
            } else if (track.getClass() == FileDescriptor.class) {
                setDataSource((FileDescriptor) track);
            }
        } catch (IOException ignored) {
        }

        broadcast(AUDIO_TRACK_CHANGED);
    }

    public void setDataSource(Object dataSource) {
        try {
            if (dataSource.getClass() == String.class) {
                setDataSource((String) dataSource);
            } else if (dataSource.getClass() == Uri.class) {
                setDataSource((Uri) dataSource);
            } else if (dataSource.getClass() == FileDescriptor.class) {
                setDataSource((FileDescriptor) dataSource);
            } else if (dataSource.getClass() == List.class) {
                setDataSource((List) dataSource);
            } else
                throw new IllegalArgumentException("AudioView supports only String, Uri, FileDescriptor data sources now.");
        } catch (IOException ignored) {
            throw new IllegalArgumentException("AudioView supports only String, Uri, FileDescriptor data sources now.");
        }
    }

    public void addToPlaylist(Object item) throws RuntimeException {
        if (mTracks == null)
            return;
        if (item.getClass() == String.class) {
            mTracks.add(item);
        } else if (item.getClass() == Uri.class) {
            mTracks.add(item);
        } else if (item.getClass() == FileDescriptor.class) {
            mTracks.add(item);
        } else if (item.getClass() == List.class) {
            mTracks.add(item);
        } else
            throw new IllegalArgumentException("AudioView supports only String, Uri, FileDescriptor data sources now.");
    }

    public void setDataSource(List tracks) throws RuntimeException {
        if (tracks.size() > 0) {
            Object itemClass = tracks.get(0);
            boolean isCorrectClass = itemClass instanceof String || itemClass instanceof Uri || itemClass instanceof FileDescriptor;

            if (!isCorrectClass)
                throw new RuntimeException("AudioView supports only String, Uri, FileDescriptor data sources now.");

            //noinspection unchecked
            mTracks = new ArrayList(tracks);
            mCurrentTrack = 0;
            selectTrack();
        }
    }

    public void setDataSource(String path) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(path);
            prepare(path);
        } catch (IllegalStateException e) {
            initMediaPlayer();
        }
    }

    public void setDataSource(Uri uri) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(this, uri);
            prepare(uri);
        } catch (IllegalStateException e) {
            initMediaPlayer();
        }
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(fd);
            prepare(fd);
        } catch (IllegalStateException e) {
            initMediaPlayer();
        }
    }

    private void release() {
        mAttachedTag = Integer.MIN_VALUE;
        try {
            if (mIsPrepared)
                mMediaPlayer.stop();
            mMediaPlayer.release();
        } catch (Exception ignored) {
        }
        mIsPrepared = false;
        SERVICE_RUNNING = false;
    }

    public void reset() {
        mIsPrepared = false;
        try {
            mMediaPlayer.reset();
        } catch (Exception ignored) {
        }
        if (mUiThread != null)
            mUiThread.interrupt();
    }

    private void prepare(Object source) {
        try {
            mMediaPlayer.prepareAsync();
            mCurrentSource = source;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        mContentView.setImageViewResource(R.id.play, R.drawable.ic_pause_white_24dp);
        mNotificationManager.notify(AUDIO_SERVICE_NOTIFICATION, mBuilder.build());
        if (mIsPrepared) {
            try {
                mMediaPlayer.start();
                broadcast(AUDIO_STARTED);
                startUpdateThread();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void pause() {
        mContentView.setImageViewResource(R.id.play, R.drawable.ic_play_arrow_white_24dp);
        mNotificationManager.notify(AUDIO_SERVICE_NOTIFICATION, mBuilder.build());
        try {
            if (mIsPrepared && mMediaPlayer.isPlaying())
                mMediaPlayer.pause();
        } catch (IllegalStateException ignored) {
        }

        if (mUiThread != null)
            mUiThread.interrupt();
        broadcast(AUDIO_PAUSED);
    }

    public void stop() {
        mContentView.setImageViewResource(R.id.play, R.drawable.ic_play_arrow_white_24dp);
        mNotificationManager.notify(AUDIO_SERVICE_NOTIFICATION, mBuilder.build());
        try {
            if (mIsPrepared)
                mMediaPlayer.stop();
        } catch (IllegalStateException ignored) {
        }

        if (mUiThread != null)
            mUiThread.interrupt();
        broadcast(AUDIO_STOPPED);
    }

    public void seekTo(Integer progress) {
        try {
            mMediaPlayer.seekTo(progress);
        } catch (IllegalStateException ignored) {
        }
    }

    public void setLoop(boolean loop) {
        mLoop = loop;
    }

    public String getTrackTitle() {
        return Util.getTrackTitle(this, mCurrentSource);
    }

    public String formatTime(boolean full) {
        int current = getCurrentPosition();
        if (full)
            return Util.formatTime(current) + " / " + Util.formatTime(getTotalDuration());
        return Util.formatTime(current);
    }
}
