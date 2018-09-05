 [ ![Download](https://api.bintray.com/packages/4ert/maven/audioview/images/download.svg) ](https://bintray.com/4ert/maven/audioview/_latestVersion)

# AudioView
Simple Android audio view with a few controls. Basically it's a MediaPlayer wrapper.

[See demo](https://raw.githubusercontent.com/4eRTuk/audioview/master/demo.png)

## Usage with Gradle
``` gradle
dependencies {
    implementation 'com.4ert:audioview:0.3.2'
}
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
