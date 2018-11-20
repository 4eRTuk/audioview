/*
 *           Copyright © 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import com.keenfin.audioview.AudioService;

import java.util.ArrayList;

import static com.keenfin.audioviewdemo.MainActivity.URL;

public class ListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        Intent audioService = new Intent(this, AudioService.class);
        audioService.setAction(AudioService.ACTION_START_AUDIO);
        startService(audioService);

        RecyclerView recycler = findViewById(R.id.recycler);
        int orientation = LinearLayoutManager.VERTICAL;
        LinearLayoutManager manager = new LinearLayoutManager(this, orientation, false);
        recycler.setLayoutManager(manager);

        ArrayList<Audio> objects = new ArrayList<>();
        objects.add(new Audio(URL, URL));
        searchForAudio(objects);
        AudioAdapter adapter = new AudioAdapter(objects);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent audioService = new Intent(this, AudioService.class);
        stopService(audioService);
    }

    private void searchForAudio(ArrayList<Audio> objects) {
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor cursor = resolver.query(uri, null, selection, null, sortOrder);
        int count;

        if (cursor != null) {
            count = cursor.getCount();
            if (count > 0 && cursor.moveToFirst()) {
                do {
                    String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                    String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                    objects.add(new Audio(title, data));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }
}
