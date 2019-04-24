/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.content.*;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

import static com.keenfin.audioview.AudioService.*;

public class AudioView2 extends BaseAudioView implements View.OnClickListener {
    private boolean mFrozen = false;
    private int mSeekTo = -1;
    private int mTag;
    protected Object mDataSource;
    private AudioService.AudioServiceBinder mServiceBinder = null;
    private View mClickedView;

    private AudioService getService() {
        return mServiceBinder != null ? mServiceBinder.getService() : null;
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mServiceBinder = ((AudioService.AudioServiceBinder) iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mServiceBinder = null;
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
                            mTime.setText(getService().formatTime(mTotalTime == null));
                        } else {
                            if (mProgress.getProgress() < current)
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
                    mTime.setText(getService().formatTime(mTotalTime == null));
                }
                mSeekTo = -1;
                mFrozen = false;
            }
        });
    }

    private void bindAudioService() {
        if (getService() == null) {
            Intent intent = new Intent(getContext(), AudioService.class);
            getContext().bindService(intent, mServiceConnection, 0);
        }
    }

    private void unbindAudioService() {
        if (getService() != null) {
            try {
                getContext().unbindService(mServiceConnection);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bindAudioService();
        IntentFilter filter = new IntentFilter(ACTION_STATUS_AUDIO);
        getContext().registerReceiver(mAudioReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unbindAudioService();
        try {
            getContext().unregisterReceiver(mAudioReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onClick(View view) {
        if (getService() == null)
            return;
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
            getService().previousTrack();
        } else if (id == R.id.forward) {
            getService().nextTrack();
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

    public void setTag(int tag) {
        mTag = tag;
    }

    public boolean attached() {
        return getService() != null && getService().getAttachedTag() == mTag;
    }
}
