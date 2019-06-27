/*
 *           Copyright © 2015-2016, 2018-2019 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.keenfin.audioview.AudioService;
import com.keenfin.audioview.AudioView;
import com.keenfin.sfcdialog.SimpleFileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String URL = "https://programmerguru.com/android-tutorial/wp-content/uploads/2013/04/hosannatelugu.mp3";
    public static final int READ_PERMISSION = 234;
    private SimpleFileChooser mSFCDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            ((AudioView) findViewById(R.id.live)).setDataSource(Uri.parse(URL));
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
            case READ_PERMISSION:
                if (SimpleFileChooser.isGrantResultOk(grantResults))
                    openList(null);
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void openList(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION);
                return;
            }
        }
        Intent intent = new Intent(this, ListActivity.class);
        startActivity(intent);
    }
}
