# Cordova plugin for PixLive SDK  [https://github.com/vidinoti/cordova-plugin-PixLive](https://github.com/vidinoti/cordova-plugin-PixLive)

This allows a seamless bridge for using Augmented Reality PixLive SDK into your own Cordova application.

Check [https://www.vidinoti.com/](https://www.vidinoti.com/) for more information and [https://vidinoti.github.io/](https://vidinoti.github.io/) for the complete documentation.

## Installation
Create a PixLive Maker account, obtain a licence, download SDKs and register you app at [http://pixlivemaker.com](http://pixlivemaker.com)


Install the plugin by passing the PixLive SDK `VDARSDK.framework`, `vdarsdk-release.aar` file paths and the license to the plugin installation command line:

```bash
cordova plugin add cordova-plugin-pixlive@latest --variable LICENSE_KEY=MyLicenseKey --variable PIXLIVE_SDK_IOS_LOCATION=\"path/to/VDARSDK.framework\" --variable PIXLIVE_SDK_ANDROID_LOCATION=\"path/to/android/vdarsdk-release.aar\"
```


## Create an augmented reality app in two minutes!
The best way to start developing your app is with a simple example. This demo app show you the basis for using PixLive SDK in your cordova project.

* Create a new Cordova project using cordova-template-PixLive. **Don't forget to register your app on http://pixlivemaker.com.**

```bash
cordova create myArApp com.mycompany.myArApp myArApp --template https://github.com/vidinoti/cordova-template-PixLive.git
```

* Add Android and iOS platforms

```bash
cd myArApp
cordova platform add android
cordova platform add ios
```

* Add the Cordova plugin for PixLive SDK to your project

```bash
cordova plugin add cordova-plugin-pixlive@latest --variable LICENSE_KEY=MyLicenseKey --variable PIXLIVE_SDK_IOS_LOCATION=\"path/to/VDARSDK.framework\" --variable PIXLIVE_SDK_ANDROID_LOCATION=\"path/to/android/vdarsdk-release.aar\"
```

* Build your project
```bash
cordova build android
cordova build ios
```

* Your demo app is ready, you can already test it!
```bash
cordova run android
```


## The basis of building an AR app with PixLive SDK

* Content synchronization
To be able to trigger AR content you need to synchronize your app with your PixLive Maker account first.
```javascript
//Synchronize the app with PixLive Maker http://pixlivemaker.com
if (window.cordova && window.cordova.plugins && window.cordova.plugins.PixLive) {
    // You can pass an array of tags to synchronize with specific tags: synchronize(['test1','test2'])
    cordova.plugins.PixLive.synchronize([], synchSuccessListener, synchErrorListener);
}
```

* Create an ARview
The first time you open an ARview you need to create it.
```javascript
/**
 * Example of arView size
 * @return {number[]} [screenWidth,screenHeight]
 */
getArViewSize = function() {
    if(window.orientation == 90 || window.orientation == -90) {
        if(window.screen.height > window.screen.width) {
            return [window.screen.height, window.screen.width];
        }
    }
    return [window.screen.width, window.screen.height];
};
if (window.cordova && window.cordova.plugins && window.cordova.plugins.PixLive) {
	var arViewSize = getArViewSize();
	var arView = cordova.plugins.PixLive.createARView(0, 0, arViewSize[0], arViewSize[1]);
}
```

* Handle the orientation changes
```javascript
/**
 * onOrientationchange Event listener
 */
onOrientationchange = function() {
	if(arView) {
	 var screenSize = getSize();
	 arView.resize(0, 0, screenSize[0], screenSize[1]);
	}
};
window.addEventListener("orientationchange", onOrientationchange, false);
```

* Close the arView
```javascript
if(arView) {
   arView.beforeLeave();
   arView.afterLeave();
}
```

* Reopen the arView
```javascript
if(arView) {
    arView.beforeEnter();
    onOrientationchange();
    arView.afterEnter();
}
```

* Handle PixLive events
```javascript
//event listeners for pixlive events
var pxlEventListeners={};
//Event handler for pixlive events
var pxlEventHandler = function(event) {
    if(event.type && pxlEventListeners[event.type]) {
        for(var i = pxlEventListeners[event.type].length-1; i>=0; i--) {
            pxlEventListeners[event.type][i](event);
        }
    }
};
/**
 * Add a new listener for the provided event type. 
 * @param {string} event The event to register for. 
 * @param {function} callback The function to be called when the provided event is generated.
 */
addListener = function(event, callback) {
	if(!pxlEventListeners[event]) {
	    pxlEventListeners[event]=[];
	}
	pxlEventListeners[event].push(callback);
}
//register pxlEventHandler
if (window.cordova && window.cordova.plugins && window.cordova.plugins.PixLive && !window.cordova.plugins.PixLive.onEventReceived) {
   cordova.plugins.PixLive.onEventReceived = pxlEventHandler;
}
```
PixLive events are: 
	* pxlContextEnter - When enter a context (recognize image, enter beacon area)
	* pxlContextExit - When exist a context
	* pxlCodeRecognize - When a QR code is recognized
	* pxlAnnotationsPresent - When content is shown
	* pxlAnnotationsHide - When content is hidden
	* pxlSynchronizationRequired - When a context ask to synchronize with given tags
	* pxlSensorTriggered - When a sensor is detected
	* pxlSensorUpdate - When a sensor value is updated
	* pxlSensorUntriggered - When a sensor is not detected anymore
	* pxlEventFromContent - When an event is generate from the content (PixLiveJS)

For example:
```javascript
//enable PixLive SDK to catch the touch event when a content is displayed
addListener("presentAnnotations",function(event){
    arView.enableTouch();
});
//disable PixLive SDK to catch the touch event when a content is hidden
addListener("hideAnnotations",function(event){
    arView.disableTouch();
});
//take action when a QR code is recognized by the SDK
addListener("codeRecognize",function(event){
    alert("QR code recognized: " + event.code);
});
```
