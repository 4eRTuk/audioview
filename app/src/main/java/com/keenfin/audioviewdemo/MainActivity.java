/*
 *           Copyright © 2015-2016, 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import com.keenfin.audioview.AudioService;
import com.keenfin.audioview.AudioView;
import com.keenfin.sfcdialog.SimpleFileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String URL = "http://programmerguru.com/android-tutorial/wp-content/uploads/2013/04/hosannatelugu.mp3";
    private SimpleFileChooser mSFCDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            ((AudioView) findViewById(R.id.live)).setDataSource(URL);
            ((AudioView) findViewById(R.id.custom_layout)).setDataSource(URL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent audioService = new Intent(this, AudioService.class);
        audioService.setAction(AudioService.ACTION_STOP_AUDIO);
        stopService(audioService);
    }

    public void selectTrack(View view) {
        mSFCDialog = new SimpleFileChooser();
        mSFCDialog.setOnChosenListener(new SimpleFileChooser.SimpleFileChooserListener() {
            @Override
            public void onFileChosen(File file) {
                try {
                    ArrayList<String> a = new ArrayList<>();
                    a.add(file.getPath());
                    ((AudioView) findViewById(R.id.audioview)).setDataSource(a);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDirectoryChosen(File file) {

            }

            @Override
            public void onCancel() {

            }
        });

        if (SimpleFileChooser.isPermissionGranted(this))
            showDialog();
        else
            mSFCDialog.requestPermission(this);
    }

    private void showDialog() {
        mSFCDialog.show(getFragmentManager(), "SelectTrackDialog");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case SimpleFileChooser.PERMISSION_REQUEST:
                if (SimpleFileChooser.isGrantResultOk(grantResults))
                    showDialog();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void openList(View view) {
        Intent intent = new Intent(this, ListActivity.class);
        startActivity(intent);
    }
}
