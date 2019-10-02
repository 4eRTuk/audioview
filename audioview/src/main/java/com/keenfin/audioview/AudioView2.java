/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.app.ActivityManager;
import android.content.*;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

import static com.keenfin.audioview.AudioService.*;

public class AudioView2 extends BaseAudioView implements View.OnClickListener {
    private boolean mFrozen = false;
    private boolean mAutoStartService = true;
    private int mSeekTo = -1;
    private int mTag;
    private int mServiceNotificationId = 1;
    private int mServiceNotificationIcon = R.drawable.thumb;
    private boolean mServiceNotificationShowClose = true;
    private boolean mServiceNotificationMinified = false;
    protected Object mDataSource;
    private AudioService.AudioServiceBinder mServiceBinder = null;
    private View mClickedView;
    private boolean mFixPlayback;

    private AudioService getService() {
        return mServiceBinder != null ? mServiceBinder.getService() : null;
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//            Log.d("AudioView", "connected");
            mServiceBinder = ((AudioService.AudioServiceBinder) iBinder);

            if (mFixPlayback) {
                onClick(findViewById(R.id.play));
                mFixPlayback = false;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mServiceBinder = null;
            try {
                getContext().unregisterReceiver(mAudioReceiver);
            } catch (Exception ignored) {
            }
        }
    };

    private BroadcastReceiver mAudioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra("status", -1);
//            Log.d("AudioView", "got: " + status + " tag: " + mTag);
            switch (status) {
                case AUDIO_STOPPED:
                case AUDIO_PAUSED:
                    setPlayIcon();
                    break;
                case AUDIO_SERVICE_STARTED:
                    if (mAutoStartService && intent.getIntExtra("tag", Integer.MIN_VALUE) == mTag)
                        if (mServiceBinder != null)
                            onClick(findViewById(R.id.play));
                        else
                            mFixPlayback = true;
                    break;
                case AUDIO_SERVICE_STOPPED:
                    unbindAudioService();
                    mServiceBinder = null;
                    mProgress.setProgress(0);
                    if (mTime != null)
                        mTime.setText("");
                    break;
            }

            if (getService() == null || !attached())
                return;

            switch (status) {
                case AUDIO_PREPARED:
                    if (mShowTitle) {
                        try {
                            mTitle.setText(getService().getTrackTitle());
                        } catch (Exception ignored) {
                        }
                    }

                    setDuration(getService().getTotalDuration());

                    if (mAudioViewListener != null)
                        mAudioViewListener.onPrepared();

                    if (mClickedView != null) {
                        onClick(mClickedView);
                        mClickedView = null;
                    }
                    break;
                case AUDIO_STARTED:
                    setPauseIcon();
                    break;
                case AUDIO_STOPPED:
                    setDuration(getService().getTotalDuration());
                    break;
                case AUDIO_PROGRESS_UPDATED:
                    if (!mFrozen) {
                        int current = getService().getCurrentPosition();
                        if (getService().getTotalDuration() < 0) {
                            if (mIndeterminate.getVisibility() == GONE) {
                                setDuration(-1);
                                setPauseIcon();
                            }
                            if (mTime != null)
                                mTime.setText(getService().formatTime(mTotalTime == null));
                        } else {
                            mProgress.setProgress(current);
                        }
                    }
                    break;
                case AUDIO_COMPLETED:
                    setDuration(getService().getTotalDuration());
                    setPlayIcon();
                    if (mAudioViewListener != null)
                        mAudioViewListener.onCompletion();
                    break;
                case AUDIO_TRACK_CHANGED:
                    mProgress.setProgress(0);
                    break;
            }
        }
    };

    public AudioView2(Context context) {
        super(context);
    }

    public AudioView2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AudioView2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void init(@Nullable Context context, AttributeSet attrs) {
        super.init(context, attrs);
        if (isInEditMode())
            return;

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (getService() == null || !attached())
                    return;

                if (fromUser)
                    mSeekTo = progress;

                if (mTime != null)
                    mTime.setText(getService().formatTime(mTotalTime == null));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mFrozen = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (getService() != null && getService().isPrepared() && attached()) {
                    getService().seekTo(mSeekTo);
                    if (mTime != null)
                        mTime.setText(getService().formatTime(mTotalTime == null));
                }
                mSeekTo = -1;
                mFrozen = false;
            }
        });
    }

    private void bindAudioService() {
        Intent intent = new Intent(getContext(), AudioService.class);
        boolean b = getContext().getApplicationContext().bindService(intent, mServiceConnection, 0);
//        Log.d("AudioView", "binded " + b);
    }

    private void unbindAudioService() {
        try {
            getContext().getApplicationContext().unbindService(mServiceConnection);
        } catch (Exception ignored) {
            Log.d("AudioView", ignored.getLocalizedMessage());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bindAudioService();
        IntentFilter filter = new IntentFilter(ACTION_STATUS_AUDIO);
        getContext().registerReceiver(mAudioReceiver, filter);
        if (!attached() || attached() && !getService().isPlaying())
            setPlayIcon();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unbindAudioService();
    }

    @Override
    public void onClick(View view) {
        if (mAutoStartService && !SERVICE_RUNNING) {
            Intent audioService = new Intent(getContext(), AudioService.class);
            audioService.putExtra("tag",  mTag);
            audioService.putExtra(AUDIO_NOTIFICATION_SHOW_CLOSE,  mServiceNotificationShowClose);
            audioService.putExtra(AUDIO_NOTIFICATION_MINIFIED,  mServiceNotificationMinified);
            audioService.putExtra(AUDIO_NOTIFICATION_CHANNEL_ID,  mServiceNotificationId);
            audioService.putExtra(AUDIO_NOTIFICATION_ICON_RES,  mServiceNotificationIcon);
            getContext().getApplicationContext().startService(audioService);
        }

        if (getService() == null) {
            bindAudioService();
            return;
        }

        if (!attached()) {
            getService().attachTag(mTag);
            setLoop(mLoop);
            setDataSource(mDataSource);
            mClickedView = view;
            return;
        }

        int id = view.getId();
        if (id == R.id.play) {
            getService().controlAudio();
        } else if (id == R.id.rewind) {
            previousTrack();
        } else if (id == R.id.forward) {
            nextTrack();
        }
    }

    private void setDataSource(Object dataSource) {
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
        } catch (IOException | NullPointerException ignored) {
        }
    }

    @Override
    public void setLoop(boolean loop) {
        super.setLoop(loop);
        if (getService() == null || !attached())
            return;
        getService().setLoop(loop);
    }

    @Override
    public void setDataSource(List tracks) throws RuntimeException {
        mDataSource = tracks;
        if (getService() == null || !attached())
            return;
        getService().setDataSource(tracks);
    }

    @Override
    public void setDataSource(String path) throws IOException {
        mDataSource = path;
        if (getService() == null || !attached())
            return;
        if (getService().isPlaying())
            return;
        getService().setDataSource(path);
    }

    @Override
    public void setDataSource(Uri uri) throws IOException {
        mDataSource = uri;
        if (getService() == null || !attached())
            return;
        getService().setDataSource(uri);
    }

    @Override
    public void setDataSource(FileDescriptor fd) throws IOException {
        mDataSource = fd;
        if (getService() == null || !attached())
            return;
        getService().setDataSource(fd);
    }

    @Override
    public void start() {
        if (getService() != null && attached())
            getService().start();
    }

    @Override
    public void pause() {
        if (getService() != null && attached())
            getService().pause();
    }

    @Override
    public void stop() {
        if (getService() != null && attached())
            getService().stop();
    }

    @Override
    public void nextTrack() {
        if (getService() != null && attached())
            getService().nextTrack();
    }

    @Override
    public void previousTrack() {
        if (getService() != null && attached())
            getService().previousTrack();
    }

    public void setTag(int tag) {
        mTag = tag;
    }

    public void setAutoStartService(boolean autostart) {
        mAutoStartService = autostart;
    }

    public void setServiceNotificationId(int id) {
        mServiceNotificationId = id;
    }

    public void setServiceNotificationIcon(int icon) {
        mServiceNotificationIcon = icon;
    }

    public void setServiceNotificationShowClose(boolean showClose) {
        mServiceNotificationShowClose = showClose;
    }

    public void setServiceNotificationMinified(boolean minified) {
        mServiceNotificationMinified = minified;
    }

    public boolean attached() {
        return getService() != null && getService().getAttachedTag() == mTag;
    }
}
