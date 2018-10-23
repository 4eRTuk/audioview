/*
 *           Copyright © 2015-2016, 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioviewdemo;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.keenfin.audioview.AudioView;
import com.keenfin.sfcdialog.SimpleFileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private SimpleFileChooser mSFCDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            String url = "http://programmerguru.com/android-tutorial/wp-content/uploads/2013/04/hosannatelugu.mp3";
            ((AudioView) findViewById(R.id.live)).setDataSource(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
}
