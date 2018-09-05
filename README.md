[ ![Download](https://api.bintray.com/packages/4ert/maven/audioview/images/download.svg) ](https://bintray.com/4ert/maven/audioview/_latestVersion)

# AudioView
Simple Android audio view with a few controls. Basically it's a MediaPlayer wrapper.

[See picture](https://raw.githubusercontent.com/4eRTuk/audioview/master/demo.png)

[See demo app](https://github.com/4eRTuk/audioview/tree/master/app)

## Usage with Gradle

1. **Add dependency**

``` gradle
dependencies {
    implementation 'com.4ert:audioview:0.3.2'
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

## Styles
#### primaryColor
Set default color for FAB and SeekBar. By default it uses colorAccent from AppTheme.

#### minified
Use alternative version of layout if true.

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
    app:showTitle="false"/>
```
