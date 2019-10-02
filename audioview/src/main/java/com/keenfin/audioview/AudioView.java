/*
 *           Copyright © 2015-2016, 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.keenfin.audioview.Util.formatTime;
import static com.keenfin.audioview.Util.getTrackTitle;

public class AudioView extends BaseAudioView implements View.OnClickListener {
    enum SEEKBAR_STATE {STICK, UNSTICK, PROGRESS}

    protected MediaPlayer mMediaPlayer;
    protected ArrayList<Object> mTracks;
    protected Object mCurrentSource;

    protected int mCurrentTrack = 0;
    protected boolean mIsPrepared = false;
    protected boolean mIsAttached = false;
    protected boolean mWasPlaying;

    protected long mProgressDelay;
    protected Handler mHandler;

    public AudioView(Context context) {
        super(context);
    }

    public AudioView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void init(@Nullable Context context, AttributeSet attrs) {
        super.init(context, attrs);
        if (isInEditMode())
            return;

        mTracks = new ArrayList<>();
        initMediaPlayer();
        createUpdateHandler();

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!mIsPrepared)
                    return;
                if (fromUser) {
                    try {
                        mMediaPlayer.seekTo(progress);
                    } catch (IllegalStateException ignored) {
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                try {
                    seekBar.setTag(isPlaying());
                    if (isPlaying())
                        mMediaPlayer.pause();
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                try {
                    if ((boolean) seekBar.getTag())
                        mMediaPlayer.start();
                } catch (IllegalStateException ignored) {
                }
            }
        });
    }

    private void createUpdateHandler() {
        final Runnable seekBarUpdateTask = new Runnable() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(SEEKBAR_STATE.PROGRESS.ordinal());
                mHandler.postDelayed(this, mProgressDelay);
            }
        };

        mHandler = new Handler(getContext().getMainLooper(), new Handler.Callback() {
            Thread mUiThread;

            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == SEEKBAR_STATE.UNSTICK.ordinal()) {
                    if (mUiThread != null && !mUiThread.isInterrupted())
                        mUiThread.interrupt();
                    return true;
                } else if (msg.what == SEEKBAR_STATE.STICK.ordinal()) {
                    mUiThread = new Thread(seekBarUpdateTask);
                    mUiThread.start();
                    mProgress.setProgress(getCurrentPosition());
                    return true;
                } else if (msg.what == SEEKBAR_STATE.PROGRESS.ordinal()) {
                    if (mIsPrepared) {
                        int current = getCurrentPosition();
                        if (mProgress.getProgress() < current) {
                            mProgress.setProgress(current);
                            if (mTotalTime != null && mTime != null)
                                mTime.setText(formatTime(getCurrentPosition()));
                            else if (mTime != null)
                                mTime.setText(getTrackTime());
                        }
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (isCorrectTrack(mCurrentTrack + 1)) {
                    mCurrentTrack++;
                    selectTrack(true);
                } else {
                    if (!mLoop) {
                        pause();
                        mProgress.setProgress(getTotalDuration());
                        if (mAudioViewListener != null)
                            mAudioViewListener.onCompletion();
                        return;
                    }

                    if (isCorrectTrack(0)) {
                        mCurrentTrack = 0;
                        selectTrack(true);
                    } else {
                        pause();
                        start();
                    }
                }
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (!mIsAttached)
                    return;
                mIsPrepared = true;
                if (mShowTitle) {
                    try {
                        mTitle.setText(getTrackTitle(getContext(), mCurrentSource));
                    } catch (Exception ignored) {
                    }
                }

                int duration = mp.getDuration();
                setDuration(duration);
                if (duration > 0) {
                    mProgressDelay = mp.getDuration() / 100;
                    if (mProgressDelay < 1000) {
                        if (mProgressDelay < 100)
                            mProgressDelay = 100;
                    } else
                        mProgressDelay = 1000;
                }

                if (mAudioViewListener != null)
                    mAudioViewListener.onPrepared();

                if (mWasPlaying) {
                    try {
                        mMediaPlayer.start();
                    } catch (IllegalStateException ignored) {
                    }
                    setPauseIcon();
                } else
                    setPlayIcon();
            }
        });

        boolean fix = mCurrentSource != null && mTracks.size() == 0;
        if (fix)
            mTracks.add(mCurrentSource);
        if (mTracks.size() > 0)
            selectTrack(false);
        if (fix)
            mTracks.remove(0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsAttached = true;
        if (!mIsPrepared)
            initMediaPlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttached = false;
        mMediaPlayer.release();
        mIsPrepared = false;
    }

    public boolean isPlaying() {
        try {
            return mMediaPlayer.isPlaying();
        } catch (IllegalStateException ignored) {
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.play) {
            controlAudio();
        } else if (i == R.id.rewind) {
            previousTrack();
        } else if (i == R.id.forward) {
            nextTrack();
        }
    }

    @Override
    public void previousTrack() {
        if (isCorrectTrack(mCurrentTrack - 1))
            mCurrentTrack--;
        else
            return;

        selectTrack(false);
    }

    @Override
    public void nextTrack() {
        if (isCorrectTrack(mCurrentTrack + 1))
            mCurrentTrack++;
        else
            return;

        selectTrack(false);
    }

    protected boolean isCorrectTrack(int trackPosition) {
        return mTracks.size() > 0 && trackPosition >= 0 && trackPosition < mTracks.size();
    }

    protected void controlAudio() {
        if (mIsPrepared && isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    protected void selectTrack(boolean play) {
        if (mTracks.size() < 1)
            return;

        Object track = mTracks.get(mCurrentTrack);
        mWasPlaying = isPlaying() || play;

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
    }

    @Override
    public void setDataSource(List tracks) throws RuntimeException {
        if (tracks.size() > 0) {
            Object itemClass = tracks.get(0);
            boolean isCorrectClass = itemClass instanceof String || itemClass instanceof Uri || itemClass instanceof FileDescriptor;

            if (!isCorrectClass)
                throw new RuntimeException("AudioView supports only String, Uri, FileDescriptor data sources now.");

            //noinspection unchecked
            mTracks = new ArrayList(tracks);
            mCurrentTrack = 0;
            selectTrack(false);
        }
    }

    @Override
    public void setDataSource(String path) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(path);
            prepare(path);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void setDataSource(Uri uri) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(getContext(), uri);
            prepare(uri);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void setDataSource(FileDescriptor fd) throws IOException {
        reset();
        try {
            mMediaPlayer.setDataSource(fd);
            prepare(fd);
        } catch (IllegalStateException ignored) {
        }
    }

    protected void reset() {
        mIsPrepared = false;
        mMediaPlayer.reset();
    }

    protected void prepare(Object source) {
        mMediaPlayer.prepareAsync();
        mCurrentSource = source;
    }

    @Override
    public void start() {
        if (mIsPrepared) {
            try {
                mMediaPlayer.start();
            } catch (IllegalStateException ignored) {
            }

            setPauseIcon();
            mHandler.sendEmptyMessage(SEEKBAR_STATE.STICK.ordinal());
        }
    }

    @Override
    public void pause() {
        try {
            if (mIsPrepared && mMediaPlayer.isPlaying())
                mMediaPlayer.pause();
        } catch (IllegalStateException ignored) {
        }

        setPlayIcon();
        mHandler.sendEmptyMessage(SEEKBAR_STATE.UNSTICK.ordinal());
    }

    @Override
    public void stop() {
        try {
            if (mIsPrepared && mMediaPlayer.isPlaying())
                mMediaPlayer.stop();
        } catch (IllegalStateException ignored) {
        }

        setPlayIcon();
        mHandler.sendEmptyMessage(SEEKBAR_STATE.UNSTICK.ordinal());
    }

    public int getCurrentPosition() {
        try {
            return mMediaPlayer.getCurrentPosition();
        } catch (IllegalStateException ignored) {
        }
        return 0;
    }

    public int getTotalDuration() {
        try {
            return mMediaPlayer.getDuration();
        } catch (IllegalStateException ignored) {
        }
        return 0;
    }

    protected String getTrackTime() {
        return formatTime(getCurrentPosition()) + " / " + formatTime(getTotalDuration());
    }
}
