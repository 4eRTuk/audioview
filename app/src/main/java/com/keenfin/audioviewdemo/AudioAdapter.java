/*
 *           Copyright © 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.keenfin.audioview.AudioView2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Audio> mObjects;

    AudioAdapter(List<Audio> objects) {
        if (objects != null)
            mObjects = objects;
        else
            mObjects = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_audio, parent, false);
        return new AudioHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((AudioHolder) holder).bind(position);
    }

    @Override
    public int getItemCount() {
        return mObjects.size();
    }

    class AudioHolder extends RecyclerView.ViewHolder {
        AudioHolder(View itemView) {
            super(itemView);
        }

        void bind(int position) {
            Audio object = mObjects.get(position);
            TextView order = itemView.findViewById(R.id.order);
            order.setText(object.getTitle());
            AudioView2 audio = itemView.findViewById(R.id.audioview);
            audio.setTag(position);
            if (!audio.attached())
                audio.setUpControls();
            try {
                audio.setDataSource(object.getPath());
            } catch (IOException ignored) {
            }
        }
    }
}
