/*
 *           Copyright © 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.*;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

import static com.keenfin.audioview.Util.formatDuration;

public abstract class BaseAudioView extends FrameLayout implements View.OnClickListener {
    protected ImageButton mPlay;
    protected View mRewind, mForward;
    protected TextView mTitle, mTime, mTotalTime;
    protected SeekBar mProgress;
    protected ProgressBar mIndeterminate;

    protected boolean mShowTitle = true;
    protected boolean mSelectControls = true;
    protected boolean mMinified = false;
    protected boolean mLoop = false;
    protected int mPrimaryColor = 0;
    protected int mCustomLayoutRes = 0;
    protected int mCustomPlayIconRes = 0;
    protected int mCustomPauseIconRes = 0;

    public AudioViewListener mAudioViewListener;

    public BaseAudioView(Context context) {
        super(context);
        init(null, null);
    }

    public BaseAudioView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public BaseAudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    protected void init(@Nullable Context context, AttributeSet attrs) {
        if (isInEditMode())
            return;

        if (context != null && attrs != null) {
            TypedArray styleable = context.obtainStyledAttributes(attrs, R.styleable.BaseAudioView, 0, 0);
            mShowTitle = styleable.getBoolean(R.styleable.BaseAudioView_showTitle, true);
            mSelectControls = styleable.getBoolean(R.styleable.BaseAudioView_selectControls, true);
            mMinified = styleable.getBoolean(R.styleable.BaseAudioView_minified, false);
            mCustomLayoutRes = styleable.getResourceId(R.styleable.BaseAudioView_customLayout, 0);
            mCustomPlayIconRes = styleable.getResourceId(R.styleable.BaseAudioView_customPlayIcon,
                    R.drawable.ic_play_arrow_white_24dp);
            mCustomPauseIconRes = styleable.getResourceId(R.styleable.BaseAudioView_customPauseIcon,
                    R.drawable.ic_pause_white_24dp);

            if (styleable.hasValue(R.styleable.BaseAudioView_primaryColor))
                mPrimaryColor = styleable.getColor(R.styleable.BaseAudioView_primaryColor, 0xFF000000);

            if (styleable.hasValue(R.styleable.BaseAudioView_minified) && mCustomLayoutRes != 0) {
                throw new RuntimeException("Minified attr should not be specified while using custom layout.");
            }
            styleable.recycle();
        }

        int layout;

        if (mCustomLayoutRes != 0) {
            layout = mCustomLayoutRes;
        } else {
            layout = mMinified ? R.layout.audioview_min : R.layout.audioview;
        }
        View view = inflate(getContext(), layout, null);
        addView(view);

        mPlay = findViewById(R.id.play);
        mRewind = findViewById(R.id.rewind);
        mForward = findViewById(R.id.forward);
        if (!mSelectControls) {
            if (mRewind != null)
                mRewind.setVisibility(GONE);
            if (mForward != null)
                mForward.setVisibility(GONE);
        }
        mProgress = findViewById(R.id.progress);
        mIndeterminate = findViewById(R.id.indeterminate);
        mTitle = findViewById(R.id.title);
        if (mTitle != null) {
            mTitle.setSelected(true);
            mTitle.setMovementMethod(new ScrollingMovementMethod());
            if (!mShowTitle)
                mTitle.setVisibility(GONE);
        }
        mTime = findViewById(R.id.time);
        mTotalTime = findViewById(R.id.total_time);
        mPlay.setOnClickListener(this);
        if (mRewind != null)
            mRewind.setOnClickListener(this);
        if (mForward != null)
            mForward.setOnClickListener(this);

        if (mPrimaryColor != 0) {
            mProgress.getProgressDrawable().setColorFilter(mPrimaryColor, PorterDuff.Mode.MULTIPLY);
            mIndeterminate.getIndeterminateDrawable().setColorFilter(mPrimaryColor, PorterDuff.Mode.SRC_ATOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mProgress.getThumb().setColorFilter(mPrimaryColor, PorterDuff.Mode.SRC_ATOP);
            } else {
                Drawable thumb = ContextCompat.getDrawable(getContext(), R.drawable.thumb);
                if (thumb != null) {
                    thumb.setColorFilter(mPrimaryColor, PorterDuff.Mode.SRC_ATOP);
                    mProgress.setThumb(thumb);
                }
            }

            if (mPlay instanceof FloatingActionButton) {
                FloatingActionButton mPlayFloating = (FloatingActionButton) mPlay;
                mPlayFloating.setBackgroundTintList(ColorStateList.valueOf(mPrimaryColor));
                mPlayFloating.setRippleColor(darkenColor(mPrimaryColor, 0.87f));
            }
        }
    }

    public void setUpControls() {
        mProgress.setProgress(0);
        mProgress.setVisibility(VISIBLE);
        mIndeterminate.setVisibility(GONE);
        setPlayIcon();
        if (mTime != null)
            mTime.setText("");
        if (mTotalTime != null)
            mTotalTime.setText("");
        if (mTitle != null)
            mTitle.setText("");
    }

    protected void setDuration(int duration) {
        String totalTime = formatDuration(duration);
        if (duration > 0) {
            mProgress.setVisibility(VISIBLE);
            mIndeterminate.setVisibility(GONE);
            mProgress.setProgress(0);
            mProgress.setMax(duration);
        } else {
            mProgress.setVisibility(GONE);
            mIndeterminate.setVisibility(VISIBLE);
        }

        if (mTotalTime != null)
            mTotalTime.setText(totalTime);
        else if (mTime != null)
            mTime.setText(totalTime);
    }

    public static @ColorInt
    int darkenColor(@ColorInt int color, @FloatRange(from = 0, to = 1) float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= value;
        return Color.HSVToColor(hsv);
    }

    @Override
    public void onClick(View v) {

    }

    public abstract void setDataSource(List tracks) throws RuntimeException;

    public abstract void setDataSource(String path) throws IOException;

    public abstract void setDataSource(Uri uri) throws IOException;

    public abstract void setDataSource(FileDescriptor fd) throws IOException;

    public abstract void start();

    public abstract void pause();

    public abstract void stop();

    public abstract void nextTrack();

    public abstract void previousTrack();

    public void setOnAudioViewListener(AudioViewListener audioViewListener) {
        mAudioViewListener = audioViewListener;
    }

    public void setLoop(boolean loop) {
        mLoop = loop;
    }

    protected void setPauseIcon() {
        mPlay.setImageResource(mCustomPauseIconRes);
    }

    protected void setPlayIcon() {
        mPlay.setImageResource(mCustomPlayIconRes);
    }
}
