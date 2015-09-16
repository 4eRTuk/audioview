/*
 *           Copyright © 2015 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioView extends FrameLayout implements View.OnClickListener {
    protected MediaPlayer mMediaPlayer;
    protected List mTracks;
    protected int mCurrentTrack = 0;
    protected boolean mIsPrepared = false;
    protected long mProgressDelay;

    protected FloatingActionButton mPlay;
    protected ImageButton mRewind, mForward;
    protected SeekBar mProgress;

    public AudioView(Context context) {
        super(context);
        init();
    }

    public AudioView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        View view = inflate(getContext(), R.layout.audioview, null);
        addView(view);

        mPlay = (FloatingActionButton) findViewById(R.id.play);
        mRewind = (ImageButton) findViewById(R.id.rewind);
        mForward = (ImageButton) findViewById(R.id.forward);
        mProgress = (SeekBar) findViewById(R.id.progress);
        mPlay.setOnClickListener(this);
        mRewind.setOnClickListener(this);
        mForward.setOnClickListener(this);

        mTracks = new ArrayList();
        mMediaPlayer = new MediaPlayer();

        final Handler mHandler = new Handler();
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mProgress.setProgress(mMediaPlayer.getCurrentPosition());
                    }
                    mHandler.postDelayed(this, mProgressDelay);
                }
            });
        }

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (isCorrectTrack(mCurrentTrack + 1)) {
                    mCurrentTrack++;
                    startTrack();
                } else {
                    mp.pause();
                    setPlayIcon();
                }
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mIsPrepared = true;
                mProgress.setProgress(0);
                mProgress.setMax(mp.getDuration());
                mProgressDelay = mp.getDuration() / 100;
            }
        });

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser)
                    mMediaPlayer.seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mMediaPlayer.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mMediaPlayer.start();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mMediaPlayer.release();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.play) {
            controlAudio();
        } else if (i == R.id.rewind) {
            rewindTrack();
        } else if (i == R.id.forward) {
            forwardTrack();
        }
    }

    protected void rewindTrack() {
        if (isCorrectTrack(mCurrentTrack - 1))
            mCurrentTrack--;

        startTrack();
    }

    protected void forwardTrack() {
        if (isCorrectTrack(mCurrentTrack + 1))
            mCurrentTrack++;

        startTrack();
    }

    protected boolean isCorrectTrack(int trackPosition) {
        return mTracks.size() > 0 && trackPosition >= 0 && trackPosition < mTracks.size();
    }

    private void controlAudio() {
        if (mIsPrepared && mMediaPlayer.isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    private void startTrack() {
        if (mTracks.size()  == 0)
            return;

        Object track = mTracks.get(mCurrentTrack);

        try {
            if (track.getClass() == String.class) {
                setDataSource((String) track);
            } else if (track.getClass() == Uri.class) {
                setDataSource((Uri) track);
            } else if (track.getClass() == FileDescriptor.class) {
                setDataSource((FileDescriptor) track);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDataSource(List tracks) throws RuntimeException, IOException {
        if (tracks.size() > 0) {
            Object itemClass = tracks.get(0);
            boolean isCorrectClass = itemClass.getClass() == String.class
                    || itemClass.getClass() == Uri.class
                    || itemClass.getClass() == FileDescriptor.class;

            if (!isCorrectClass)
                throw new RuntimeException("AudioView supports only String, Uri, FileDescriptor data sources now.");

            //noinspection unchecked
            mTracks = new ArrayList(tracks);
            mCurrentTrack = 0;
            startTrack();
        }
    }

    public void setDataSource(String path) throws IOException {
        mIsPrepared = false;
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(path);
        mMediaPlayer.prepare();
    }

    public void setDataSource(Uri uri) throws IOException {
        mIsPrepared = false;
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(getContext(), uri);
        mMediaPlayer.prepare();
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        mIsPrepared = false;
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(fd);
        mMediaPlayer.prepare();
    }

    public void start() {
        if (mIsPrepared) {
            mMediaPlayer.start();
            setPauseIcon();
        }
    }

    public void pause() {
        if (mIsPrepared && mMediaPlayer.isPlaying())
            mMediaPlayer.pause();

        setPlayIcon();
    }

    public void stop() {
        if (mIsPrepared && mMediaPlayer.isPlaying())
            mMediaPlayer.stop();

        setPlayIcon();
    }

    protected void setPauseIcon() {
        mPlay.setImageResource(R.drawable.ic_pause_white_24dp);
    }

    protected void setPlayIcon() {
        mPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
    }

}
