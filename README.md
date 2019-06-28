[ ![Download](https://api.bintray.com/packages/4ert/maven/audioview/images/download.svg) ](https://bintray.com/4ert/maven/audioview/_latestVersion)

# AudioView
Simple Android audio view with a few controls. Basically it's a MediaPlayer wrapper. You can choose AudioView2 and start a AudioService to start MediaPlayer in service. See below for more info (and demo app).

[See picture](https://raw.githubusercontent.com/4eRTuk/audioview/master/demo.png)

[See demo app](https://github.com/4eRTuk/audioview/tree/master/app)

## Usage with Gradle

1. **Add dependency**

``` gradle
dependencies {
    implementation 'com.4ert:audioview:1.4.5'
}
```

2. **Add layout**
``` xml
<com.keenfin.audioview.AudioView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"/>
```

3. **Set file data source**
``` java
audioView.setDataSource("/path/to/file");
audioView.setDataSource(Uri);
audioView.setDataSource(FileDescriptor);
audioView.setDataSource(List<String/Uri/FileDescriptor>);
```

4. **Control playback if needed**
``` java
audioView.start();
audioView.pause();
audioView.stop();
```


## Usage AudioView2 in AudioService
Multiple AudioView2 with different tags can attach to service and play through it, but only one at a time. It's useful while placing AudioView2 in list or recycler view.

1. **Create AudioView2 in xml or by code**

``` xml
<com.keenfin.audioview.AudioView2
    android:id="@+id/audioview"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"/>
```

2. **Start AudioService to handle playback (optional)**

``` java
Intent audioService = new Intent(this, AudioService.class);
audioService.setAction(AudioService.ACTION_START_AUDIO);
startService(audioService);
```

3. **Assign tag for view and attach to service**

``` java
AudioView2 audio = itemView.findViewById(R.id.audioview);
audio.setTag(position);
if (!audio.attached())
    audio.setUpControls();
try {
    audio.setDataSource(object.getPath());
} catch (IOException ignored) {
}
```

5. **Stop service when you do not need it anymore (optional)**

``` java
Intent audioService = new Intent(this, AudioService.class);
stopService(audioService);
```

There is a default behaviour for AudioView2 to start service automatically if it is not running yet. You can disable this by setting AudioView2.setAutoStartServie(false), but you can not omit 2 and 5 steps in this case. 


## Attach to service to implement your own behaviour
You can attach to AudioService to implement your own view or other behaviour.

1. **Add service connection**

``` java
private ServiceConnection mServiceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mService = ((AudioService.AudioServiceBinder) iBinder).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mService = null;
    }
};
```

2. **Bind/unbind to service**

``` java
private void bindAudioService() {
    Intent intent = new Intent(getContext(), AudioService.class);
    getContext().bindService(intent, mServiceConnection, 0);
}

private void unbindAudioService() {
    try {
        getContext().unbindService(mServiceConnection);
    } catch (Exception ignored) {
    }
}
```

3. **Add broadcast receiver to handle events**

``` java
private BroadcastReceiver mAudioReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra("status", -1);
        int status = intent.getIntExtra("tag", -1);
        switch (status) {
            case AUDIO_PREPARED:
            case AUDIO_STARTED:
            case AUDIO_PAUSED:
            case AUDIO_STOPPED:
            case AUDIO_PROGRESS_UPDATED:
            case AUDIO_COMPLETED:
            case AUDIO_TRACK_CHANGED:
                break;
        }
    }
};
```

4. **Register/unregister receiver**

``` java
private void registerAudioReceiver() {
    getContext().registerReceiver(mAudioReceiver, filter);
}

private void unregisterAudioReceiver() {
    try {
        getContext().unregisterReceiver(mAudioReceiver);
    } catch (Exception ignored) {
    }
}
```


## Audio notification for service
For AudioView2 and associated service foreground notification is applied. There are default playback controls plus close icon to stop and destroy service. You can setup following parameters through service intent or by proxying them over AudioView2: 
#### AUDIO_NOTIFICATION_CHANNEL_ID
#### AudioView2.setServiceNotificationId(int id)
Integer to append to default string channel id for Android 8+

#### AUDIO_NOTIFICATION_ICON_RES
#### AudioView2.setServiceNotificationIcon(int icon)
Drawable integer resource to show in status bar for notification

#### AUDIO_NOTIFICATION_SHOW_CLOSE
#### AudioView2.setServiceNotificationShowClose(boolean showClose)
Boolean to show close service button or not

#### AUDIO_NOTIFICATION_MINIFIED
#### AudioView2.setServiceNotificationMinified(boolean minified)
Boolean to hide previous/next and title views


## Styles & options
#### primaryColor
Set default color for FAB and SeekBar. By default it uses colorAccent from AppTheme.

#### minified
Use alternative version of layout if true.

#### customLayout
Specify custom player layout reference. Should contain
- TextViews R.id.time, R.id.title;
- ImageButtons R.id.rewind, R.id.forward, R.id.play;
- SeekBar R.id.progress;
- ProgressBar R.id.indeterminate.

Mutually exclusive with minified and primaryColor.
```
...
app:customLayout="@layout/my_custom_layout"
...
```

#### customPlayIcon
Set resource of play icon.

#### customPauseIcon
Set resource of pause icon.

#### selectControls
Show (true by default) or hide rewind/forward buttons. Not available if minified.

#### showTitle
Show song's title if there is one. Default is true.

``` xml
<com.keenfin.audioview.AudioView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:minified="true"
    app:primaryColor="@android:color/holo_blue_ligh"
    app:selectControls="false"
    app:showTitle="false"
    app:customPlayIcon="@drawable/my_play_icon"
    app:customPauseIcon="@drawable/my_pause_icon"/>
```

#### setLoop(boolean)
Loop playlist (or single file).
