require('cordova/channel').onCordovaReady.subscribe(function() {
    require('cordova/exec')(eventHandler, null, 'PixLive', 'installEventHandler', []);

    function eventHandler(message) {
        if(window.cordova.plugins.PixLive && window.cordova.plugins.PixLive.onEventReceived) {
        	console.log("eventHandler: ");
        	console.log(message.type);
            window.cordova.plugins.PixLive.onEventReceived(message);
        }
    }
});