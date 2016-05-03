var exec = require('cordova/exec');


/**
 * PixLive
 * @constructor
 */
var PixLive = function(handle) {
	this.options = {};
	this.handle = handle;
};

PixLive.nextViewHandle = 1;


PixLive.Context = function(prop) {
	// Apply properties to the context object
	var keys = Object.keys(prop);
	for (var j = keys.length - 1; j >= 0; j--) {
		this[keys[j]] = prop[keys[j]];
	}
};

PixLive.Context.prototype = {
	activate: function() {
		exec(null, null, "PixLive", "activateContext", [this.contextId]);
	},
	ignore: function() {
		exec(null, null, "PixLive", "ignoreContext", [this.contextId]);
	}
};

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
};

PixLive.onEventReceived = null;

PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ? (apiKey ? apiKey : true) : null]);
};

PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ? (apiKey ? apiKey : true) : null]);
};

PixLive.synchronize = function( tags, success, error ) {
	exec(success, error, "PixLive", "synchronize",  [tags]);
};

PixLive.presentNotificationsList = function(success, error) {
	exec(success, error, "PixLive", "presentNotificationsList",  []);
};

PixLive.openURLInInternalBrowser = function(url) {
	exec(success, error, "PixLive", "openURLInInternalBrowser",  [url]);
};

PixLive.getContexts = function(success, error) {
	exec(function(list) {
		if(success !== null) {
			var ret = [];
			for (var i = 0; i < list.length; i++) {
				var prop = list[i];
				var object = new PixLive.Context(prop);

				// Add the object to the array
				ret.push(object);

			}
			success(ret);
		}
	}, error, "PixLive", "getContexts",  []);
};

// Used to signal the plugin that the page is fully loaded
document.addEventListener("deviceready", function() {
	exec(null, null, "PixLive", "pageLoaded",  []);
}, false);

module.exports = PixLive;
