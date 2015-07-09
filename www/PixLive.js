var exec = require('cordova/exec');



var PixLive = function(handle) {
	this.options = {};
	this.handle = handle;
};

PixLive.nextViewHandle = 1;


PixLive.prototype = {
	beforeEnter: function() {
		exec(null, null, "PixLive", "beforeEnter", [this.handle]);
	},
	afterEnter: function() {
		exec(null, null, "PixLive", "afterEnter", [this.handle]);
	},
	beforeLeave: function() {
		exec(null, null, "PixLive", "beforeLeave", [this.handle]);
	},
	afterLeave: function() {
		exec(null, null, "PixLive", "afterLeave", [this.handle]);
	},
	destroy: function() {
		exec(null, null, "PixLive", "destroy", [this.handle]);
	},
	resize: function(originx, originy, width, height) {
		exec(null, null, "PixLive", "resize", [this.handle, originx, originy, width, height]);
	},
	disableTouch: function() {
		exec(null, null, "PixLive", "disableTouch", []);
	},
	enableTouch: function() {
		exec(null, null, "PixLive", "enableTouch", []);
	}
};

PixLive.createARView = function( originx, originy, width, height ) {
	var handle = PixLive.nextViewHandle++;

    exec(null, null, "PixLive", "createARView",  [originx, originy, width, height, handle ]);
    return new PixLive(handle);
}

PixLive.onEventReceived = null;

PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ? (apiKey ? apiKey : true) : null]);
}

PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ? (apiKey ? apiKey : true) : null]);
}

PixLive.synchronize = function( tags, success, error ) {
	exec(success, error, "PixLive", "synchronize",  [tags]);
}

PixLive.presentNotificationsList = function(success, error) {
	exec(success, error, "PixLive", "presentNotificationsList",  []);
}

PixLive.openURLInInternalBrowser = function(url) {
	exec(success, error, "PixLive", "openURLInInternalBrowser",  [url]);
}


var PixLiveInstance = new PixLive();

module.exports = PixLive;
