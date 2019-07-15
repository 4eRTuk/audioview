/*
 *           Copyright © 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import com.keenfin.audioview.AudioService;

import java.util.ArrayList;

import static com.keenfin.audioviewdemo.MainActivity.URL;

public class ListActivity extends AppCompatActivity {
    private ArrayList<Audio> mObjects;
    private AudioService mAudioService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mAudioService = ((AudioService.AudioServiceBinder) iBinder).getService();
            for (Audio item : mObjects)
                mAudioService.addToPlaylist(item.getPath());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAudioService = null;
        }
    };

    private void bindAudioService() {
        if (mAudioService == null) {
            Intent intent = new Intent(this, AudioService.class);
            getApplicationContext().bindService(intent, mServiceConnection, 0);
        }
    }

    private void unbindAudioService() {
        getApplicationContext().unbindService(mServiceConnection);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        /*
            Uncomment lines to start service separately
            And probably you want to set each AudioView2.setAutoStartService(false)
        */
//        Intent audioService = new Intent(this, AudioService.class);
//        startService(audioService);
//        stopService(audioService);

        mObjects = new ArrayList<>();
        mObjects.add(new Audio(URL, URL));
        searchForAudio(mObjects);

        AudioAdapter adapter = new AudioAdapter(mObjects);
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setAdapter(adapter);

        int orientation = LinearLayoutManager.VERTICAL;
        LinearLayoutManager manager = new LinearLayoutManager(this, orientation, false);
        recycler.setLayoutManager(manager);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindAudioService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindAudioService();
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
