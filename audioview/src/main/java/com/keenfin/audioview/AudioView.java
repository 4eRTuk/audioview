/*
 *           Copyright © 2015-2016, 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioView extends FrameLayout implements View.OnClickListener {
    enum SEEKBAR_STATE {STICK, UNSTICK}

    protected MediaPlayer mMediaPlayer;
    protected ArrayList<Object> mTracks;
    protected Object mCurrentSource;

    protected int mCurrentTrack = 0;
    protected boolean mIsPrepared = false;
    protected boolean mIsAttached = false;
    protected long mProgressDelay;

    protected FloatingActionButton mPlay;
    protected ImageButton mRewind, mForward;
    protected TextView mTitle, mTime, mTotalTime;
    protected SeekBar mProgress;
    protected Handler mHandler;

    protected boolean mShowTitle = true;
    protected boolean mSelectControls = true;
    protected boolean mMinified = false;
    protected int mPrimaryColor = 0;

    public AudioView(Context context) {
        super(context);
        init(null, null);
    }

    public AudioView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@Nullable Context context, AttributeSet attrs) {
        if (isInEditMode())
            return;

        if (context != null && attrs != null) {
            TypedArray styleable = context.obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0);
            mShowTitle = styleable.getBoolean(R.styleable.AudioView_showTitle, true);
            mSelectControls = styleable.getBoolean(R.styleable.AudioView_selectControls, true);
            mMinified = styleable.getBoolean(R.styleable.AudioView_minified, false);
            if (styleable.hasValue(R.styleable.AudioView_primaryColor))
                mPrimaryColor = styleable.getColor(R.styleable.AudioView_primaryColor, 0xFF000000);
            styleable.recycle();
        }

        int layout = mMinified ? R.layout.audioview_min : R.layout.audioview;
        View view = inflate(getContext(), layout, null);
        addView(view);

        mPlay = findViewById(R.id.play);
        mRewind = findViewById(R.id.rewind);
        mForward = findViewById(R.id.forward);
        if (!mSelectControls) {
            mRewind.setVisibility(GONE);
            mForward.setVisibility(GONE);
        }
        mProgress = findViewById(R.id.progress);
        mTitle = findViewById(R.id.title);
        mTitle.setSelected(true);
        mTitle.setMovementMethod(new ScrollingMovementMethod());
        if (!mShowTitle)
            mTitle.setVisibility(GONE);
        mTime = findViewById(R.id.time);
        mTotalTime = findViewById(R.id.total_time);
        mPlay.setOnClickListener(this);
        mRewind.setOnClickListener(this);
        mForward.setOnClickListener(this);

        if (mPrimaryColor != 0) {
            mProgress.getProgressDrawable().setColorFilter(mPrimaryColor, PorterDuff.Mode.MULTIPLY);
            mProgress.getIndeterminateDrawable().setColorFilter(mPrimaryColor, PorterDuff.Mode.MULTIPLY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mProgress.getThumb().setColorFilter(mPrimaryColor, PorterDuff.Mode.SRC_ATOP);
            }
            mPlay.setBackgroundTintList(ColorStateList.valueOf(mPrimaryColor));
            mPlay.setRippleColor(darkenColor(mPrimaryColor, 0.87f));
        }

        mTracks = new ArrayList<>();
        initMediaPlayer();

        final Runnable seekBarUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (mIsPrepared && mProgress.getProgress() < mMediaPlayer.getCurrentPosition())
                    mProgress.setProgress(mMediaPlayer.getCurrentPosition());

                mHandler.postDelayed(this, mProgressDelay);
            }
        };

        mHandler = new Handler(new Handler.Callback() {
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
                    mProgress.setProgress(mMediaPlayer.getCurrentPosition());
                    return true;
                }
                return false;
            }
        });

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!mIsPrepared)
                    return;
                if (fromUser)
                    mMediaPlayer.seekTo(progress);

                if (mTotalTime != null)
                    mTime.setText(formatTime(mMediaPlayer.getCurrentPosition()));
                else
                    mTime.setText(getTrackTime());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.setTag(mMediaPlayer.isPlaying());
                if (mMediaPlayer.isPlaying())
                    mMediaPlayer.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if ((boolean) seekBar.getTag())
                    mMediaPlayer.start();
            }
        });
    }

    public static @ColorInt
    int darkenColor(@ColorInt int color, @FloatRange(from = 0, to = 1) float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= value;
        return Color.HSVToColor(hsv);
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (isCorrectTrack(mCurrentTrack + 1)) {
                    mCurrentTrack++;
                    selectTrack();
                } else {
                    pause();
                    mProgress.setProgress(mMediaPlayer.getDuration());
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
                        mTitle.setText(getTrackTitle());
                    } catch (Exception ignored) {
                    }
                }

                int duration = mp.getDuration();
                String totalTime = "∞";
                if (duration > 0) {
                    totalTime = formatTime(duration);
                    mProgress.setProgress(0);
                    mProgress.setMax(duration);
                    mProgressDelay = mp.getDuration() / 100;
                    if (mProgressDelay < 1000) {
                        if (mProgressDelay < 100)
                            mProgressDelay = 100;
                    } else
                        mProgressDelay = 1000;
                } else {
                    mProgress.setIndeterminate(true);
                    mProgress.setThumb(null);
                }

                if (mTotalTime != null)
                    mTotalTime.setText(totalTime);
            }
        });

        boolean fix = mCurrentSource != null && mTracks.size() == 0;
        if (fix)
            mTracks.add(mCurrentSource);
        if (mTracks.size() > 0)
            selectTrack();
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

    protected void previousTrack() {
        if (isCorrectTrack(mCurrentTrack - 1))
            mCurrentTrack--;
        else
            return;

        selectTrack();
    }

    protected void nextTrack() {
        if (isCorrectTrack(mCurrentTrack + 1))
            mCurrentTrack++;
        else
            return;

        selectTrack();
    }

    protected boolean isCorrectTrack(int trackPosition) {
        return mTracks.size() > 0 && trackPosition >= 0 && trackPosition < mTracks.size();
    }

    protected void controlAudio() {
        if (mIsPrepared && mMediaPlayer.isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    protected void selectTrack() {
        if (mTracks.size() < 1)
            return;

        Object track = mTracks.get(mCurrentTrack);
        boolean wasPlaying = mMediaPlayer.isPlaying();

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

        if (wasPlaying)
            mMediaPlayer.start();
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
        mMediaPlayer.setDataSource(getContext(), uri);
        prepare(uri);
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        reset();
        mMediaPlayer.setDataSource(fd);
        prepare(fd);
    }

    protected void reset() {
        mIsPrepared = false;
        mMediaPlayer.reset();
    }

    protected void prepare(Object source) throws IOException {
        mMediaPlayer.prepareAsync();
        mCurrentSource = source;
    }

    public void start() {
        if (mIsPrepared) {
            mMediaPlayer.start();
            setPauseIcon();
            mHandler.sendEmptyMessage(SEEKBAR_STATE.STICK.ordinal());
        }
    }

    public void pause() {
        if (mIsPrepared && mMediaPlayer.isPlaying())
            mMediaPlayer.pause();

        setPlayIcon();
        mHandler.sendEmptyMessage(SEEKBAR_STATE.UNSTICK.ordinal());
    }

    public void stop() {
        if (mIsPrepared && mMediaPlayer.isPlaying())
            mMediaPlayer.stop();

        setPlayIcon();
        mHandler.sendEmptyMessage(SEEKBAR_STATE.UNSTICK.ordinal());
    }

    protected void setPauseIcon() {
        mPlay.setImageResource(R.drawable.ic_pause_white_24dp);
    }

    protected void setPlayIcon() {
        mPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
    }

    protected String getTrackTitle() {
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        if (mCurrentSource instanceof String)
            metaRetriever.setDataSource((String) mCurrentSource);
        if (mCurrentSource instanceof Uri)
            metaRetriever.setDataSource((getContext()), (Uri) mCurrentSource);
        if (mCurrentSource instanceof FileDescriptor)
            metaRetriever.setDataSource((FileDescriptor) mCurrentSource);

        String artist = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String title = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        metaRetriever.release();

        if (artist != null && title != null)
            return artist + " - " + title;
        if (artist == null && title != null)
            return title;
        return artist;
    }

    protected String getTrackTime() {
        return formatTime(mMediaPlayer.getCurrentPosition()) + " / " + formatTime(mMediaPlayer.getDuration());
    }

    @SuppressLint("DefaultLocale")
    protected String formatTime(int millis) {
        int hour, min;
        hour = min = 0;
        millis /= 1000;

        if (millis >= 60) {
            min = millis / 60;
            millis %= 60;
        }

        if (min >= 60) {
            hour = min / 60;
            min %= 60;
        }

        String result = "";
        if (hour > 0)
            result += String.format("%02d:", hour);
        result += String.format("%02d:%02d", min, millis);

        return result;
    }
}
