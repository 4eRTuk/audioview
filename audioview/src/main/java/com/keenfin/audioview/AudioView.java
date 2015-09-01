/*
 *           Copyright © 2015 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioView extends FrameLayout implements View.OnClickListener {
    protected MediaPlayer mMediaPlayer;
    protected List mTracks;
    protected int mCurrentTrack = 0;

    protected FloatingActionButton mPlay;
    protected ImageButton mRewind, mForward;

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
        mPlay.setOnClickListener(this);
        mRewind.setOnClickListener(this);
        mForward.setOnClickListener(this);

        mTracks = new ArrayList();
        mMediaPlayer = new MediaPlayer();
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
        if (mMediaPlayer.isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    private void startTrack() {
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
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(path);
        mMediaPlayer.prepare();
    }

    public void setDataSource(Uri uri) throws IOException {
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(getContext(), uri);
        mMediaPlayer.prepare();
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(fd);
        mMediaPlayer.prepare();
    }

    public void start() {
        mMediaPlayer.start();
        mPlay.setImageResource(R.drawable.ic_pause_white_24dp);
    }

    public void pause() {
        mMediaPlayer.pause();
        mPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
    }
}
