/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioService extends Service {
    public static final String ACTION_START_AUDIO = "AudioService.START";
    public static final String ACTION_STOP_AUDIO = "AudioService.STOP";
    public static final String ACTION_STATUS_AUDIO = "AudioService.STATUS";

    public static final int AUDIO_PREPARED = 0;
    public static final int AUDIO_STARTED = 1;
    public static final int AUDIO_PAUSED = 2;
    public static final int AUDIO_STOPPED = 3;
    public static final int AUDIO_PROGRESS_UPDATED = 4;
    public static final int AUDIO_COMPLETED = 5;
    public static final int AUDIO_TRACK_CHANGED = 6;

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
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        release();
    }

    @Override
    public void onDestroy() {
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
            case ACTION_START_AUDIO:
                break;
            case ACTION_STOP_AUDIO:
                release();
                stopSelf();
                break;
            default:
                break;
        }

        return START_STICKY;
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
                    mMediaPlayer.start();
                    mWasPlaying = false;
                }

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
                            if (mIsPrepared && mMediaPlayer.isPlaying())
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
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public void controlAudio() {
        if (mIsPrepared && mMediaPlayer.isPlaying()) {
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
        boolean wasPlaying = mMediaPlayer.isPlaying();
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
            }
        } catch (IOException ignored) {
            throw new IllegalArgumentException("AudioView supports only String, Uri, FileDescriptor data sources now.");
        }
    }

    public void setDataSource(List tracks) throws RuntimeException {
        if (tracks.size() > 0) {
            Object itemClass = tracks.get(0);
            boolean isCorrectClass = itemClass.getClass() == String.class || itemClass.getClass() == Uri.class || itemClass.getClass() == FileDescriptor.class;

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
        mMediaPlayer.setDataSource(path);
        prepare(path);
    }

    public void setDataSource(Uri uri) throws IOException {
        reset();
        mMediaPlayer.setDataSource(this, uri);
        prepare(uri);
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        reset();
        mMediaPlayer.setDataSource(fd);
        prepare(fd);
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
        mMediaPlayer.prepareAsync();
        mCurrentSource = source;
    }

    public void start() {
        if (mIsPrepared) {
            mMediaPlayer.start();
            broadcast(AUDIO_STARTED);
            startUpdateThread();
        }
    }

    public void pause() {
        if (mIsPrepared && mMediaPlayer.isPlaying())
            mMediaPlayer.pause();

        if (mUiThread != null)
            mUiThread.interrupt();
        broadcast(AUDIO_PAUSED);
    }

    public void stop() {
        if (mIsPrepared)
            mMediaPlayer.stop();

        if (mUiThread != null)
            mUiThread.interrupt();
        broadcast(AUDIO_STOPPED);
    }

    public void seekTo(Integer progress) {
        mMediaPlayer.seekTo(progress);
    }

    public void setLoop(boolean loop) {
        mLoop = loop;
    }

    public String getTrackTitle() {
        return Util.getTrackTitle(this, mCurrentSource);
    }

    public String formatTime(boolean full) {
        int current = mMediaPlayer.getCurrentPosition();
        if (full)
            return Util.formatTime(current) + " / " + Util.formatTime(mMediaPlayer.getDuration());
        return Util.formatTime(current);
    }
}
