/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;

import java.io.FileDescriptor;

public final class Util {
    public static String getTrackTitle(Context context, Object source) {
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        try {
            if (source instanceof String)
                metaRetriever.setDataSource((String) source);
            if (source instanceof Uri)
                metaRetriever.setDataSource(context, (Uri) source);
            if (source instanceof FileDescriptor)
                metaRetriever.setDataSource((FileDescriptor) source);
        } catch (IllegalArgumentException ignored) {
        }

        String artist = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String title = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        metaRetriever.release();

        if (artist != null && !TextUtils.isEmpty(artist) && title != null && !TextUtils.isEmpty(title))
            return artist + " - " + title;
        if ((artist == null || TextUtils.isEmpty(artist)) && title != null && !TextUtils.isEmpty(title))
            return title;
        if (artist == null || TextUtils.isEmpty(artist))
            return context.getString(R.string.no_title);
        return artist;
    }

    @SuppressLint("DefaultLocale")
    public static String formatTime(int millis) {
        if (millis < 0)
            return "∞";

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

    public static String formatDuration(int duration) {
        return duration > 0 ? formatTime(duration) : "∞";
    }
}
