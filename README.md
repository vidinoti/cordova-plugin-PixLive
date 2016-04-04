# Cordova plugin for PixLive SDK

This allows a seamless bridge for using Augmented Reality PixLive SDK into your own Cordova application.

Check [http://www.pixlive.info](http://www.pixlive.info) for more information and the Ionic plugin at [http://vidinoti.github.io/angular-pixlive/](http://vidinoti.github.io/angular-pixlive/)

## Installation

Install the plugin by passing the VDARSDK.framework and vdarsdk-release.aar file of the PixLive SDK to the plugin installation command line:

```bash
cordova plugin add cordova-plugin-pixlive@latest --variable LICENSE_KEY=MyLicenseKey --variable PIXLIVE_SDK_IOS_LOCATION=\"path/to/VDARSDK.framework\" --variable PIXLIVE_SDK_ANDROID_LOCATION=\"path/to/android/vdarsdk-release.aar\"
```
