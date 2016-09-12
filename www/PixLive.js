var exec = require('cordova/exec');

/**
* cordova-plugin-PixLive based class
* <p>Use cordova.plugins.PixLive to access the static methods. The instance methods need to be called on the PixLive
* instances returned by the createARView method, if you do not called the instance methods when it is needed problems will occur.
* <p>Your app need to be linked with your PixLive Maker account http://pixlivemaker.com. </p>
* To do so you need to register your app in the PixLive SDK->My Applications section 
* and use a valid account license (PixLive SDK->My license) when you install the plugin.</p>
* <p>Glossary:</p>
* <ul>
* <li> context : A context can be an image or a beacon.
* <li> content: A content displayed when a context is trigger. 
* </ul>
* @class
*/
var PixLive = function(handle) {
	this.options = {};
	this.handle = handle;
};

PixLive.nextViewHandle = 1;


/**
* Context
* @class
*/
PixLive.Context = function(prop) {
	// Apply properties to the context object
	var keys = Object.keys(prop);
	for (var j = keys.length - 1; j >= 0; j--) {
		this[keys[j]] = prop[keys[j]];
	}
};

PixLive.Context.prototype = {
	/**
	* activate a context (trigger the content)
	*/
	activate: function() {
		exec(null, null, "PixLive", "activateContext", [this.contextId]);
	},
	/**
	* ignore a context (the context will not be activated in the future)
	*/
	ignore: function() {
		exec(null, null, "PixLive", "ignoreContext", [this.contextId]);
	}
};

PixLive.prototype = {
	/**
	* Need to be called before enter an arView.
	*/
	beforeEnter: function() {
		exec(null, null, "PixLive", "beforeEnter", [this.handle]);
	},
	/**
	* Need to be called after enter an arView.
	*/
	afterEnter: function() {
		exec(null, null, "PixLive", "afterEnter", [this.handle]);
	},
	/**
	* Need to be called before leaving an arView
	*/
	beforeLeave: function() {
		exec(null, null, "PixLive", "beforeLeave", [this.handle]);
	},
	/**
	* Need to be called after leaving an arView
	*/
	afterLeave: function() {
		exec(null, null, "PixLive", "afterLeave", [this.handle]);
	},
	/**
	* Destroy the Ar view
	*/
	destroy: function() {
		exec(null, null, "PixLive", "destroy", [this.handle]);
	},
	/**
	* Need to be called when the screen if resized ("orientationchange" window event)
	*/
	resize: function(originx, originy, width, height) {
		exec(null, null, "PixLive", "resize", [this.handle, originx, originy, width, height]);
	},
	/**
	* disable PixLive SDK to catch the touch event when a content is displayed
	*/
	disableTouch: function() {
		exec(null, null, "PixLive", "disableTouch", []);
	},
	/**
	* enable PixLive SDK to catch the touch event when a content is displayed
	*/
	enableTouch: function() {
		exec(null, null, "PixLive", "enableTouch", []);
	}
};

/**
* Create a new AR View. Use it only once per ARView. 
* @param {integer} originx - The x origin of the AR view in pxl
* @param {integer} originy - The y origin of the AR view in pxl
* @param {integer} width - The width of the AR view in pxl
* @param {integer} height - The height of the AR view in pxl
* @returns {PixLive} - the PixLive instance related to this AR view
*/
PixLive.createARView = function( originx, originy, width, height ) {
	var handle = PixLive.nextViewHandle++;

    exec(null, null, "PixLive", "createARView",  [originx, originy, width, height, handle ]);
    return new PixLive(handle);
};

PixLive.onEventReceived = null;

/**
* Activate the notifactions support
* @param {boolean} enabled - true to enable, false to disable
* @param {string} apiKey - Google APIs project number for android app
*/
PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ? (apiKey ? apiKey : true) : null]);
};

/**
* Synchronize the app with the linked PixLive Maker account http://pixlivemaker.com
* @param {string[]} tags - An array of tags to synchronize with (can be left empty) example:
* <ul>
* <li> [] if you do not want to use tags, all the contexts from the linked PixLive Maker account will be synchronized
* <li> ['tag1','tag2'] to synchronize with the contexts that are tagged with tag1 or tag2
* <li> [['tag1','tag2'], 'tag3'] to synchronize with the contexts that are tagged with (tag1 and tag2) or tag3
* </ul>
* Specific languages can be attributated to PixLive Maker content, to synchronize your app with a specific language
* use the tag: 'lang_{{iso_code_of_the_language}}' for example:
* <ul>
* <li> ['lang_fr'] to sychronize with all the french contents
* <li> [['tag1','lang_en'], ['tag2','lang_en']] to synronize with the english contents tagged with tag1 or tag2
* </ul>
* @param {callback} success - success callback
* @param {callback} error - error callback
*/
PixLive.synchronize = function( tags, success, error ) {
	exec(success, error, "PixLive", "synchronize",  [tags]);
};

/**
* Will show the list of beacons notifications previously received
* @param {callback} success - success callback
* @param {callback} error - error callback
*/
PixLive.presentNotificationsList = function(success, error) {
	exec(success, error, "PixLive", "presentNotificationsList",  []);
};

/**
* Will open an url with the PixLive SDK internal browser
* @param {string} url - The url
*/
PixLive.openURLInInternalBrowser = function(url) {
	exec(null, null, "PixLive", "openURLInInternalBrowser",  [url]);
};

/**
* Get all the contexts that have been synchronized. A context can be an image or a beacon
* @param {callback} success(list) - success callback with the list of contexts as parameter
* @param {callback} error - error callback
*/
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

/**
* Get the context (need to have been synchronized) with the corresponding contextId. A context can be an image or a beacon
* @param {string} contextId - id of the context
* @param {callback} success(list) - success callback with the list of contexts as parameter
* @param {callback} error - error callback
*/
PixLive.getContext = function(contextId, success, error) {
	exec(function(context) {
		if(success !== null) {
			var object = new PixLive.Context(context);
			success(object);
		}
	}, error, "PixLive", "getContext",  [contextId]);
};

/**
* Activates the bookmark feature. A bookmark icon will be shown when a context is
* displayed and the user has the possibility to "save" the context.
* @param {boolean} enabled - true to enable, false to disable
*/
PixLive.setBookmarkSupport = function (enabled) {
	exec(null, null, "PixLive", "setBookmarkSupport", [enabled]);
};

/**
* Returns the list of contexts that have been bookmarked.
* @param {callback} success(list) - success callback with the list of bookmarked contexts as parameter
* @param {callback} error - error callback
*/
PixLive.getBookmarks = function(success, error) {
	exec(function(list) {
		if(success !== null) {
			var ret = [];
			for (var i = 0; i < list.length; i++) {
				var prop = list[i];
				var object = new PixLive.Context(prop);
				ret.push(object);
			}
			success(ret);
		}
	}, error, "PixLive", "getBookmarks",  []);
};

/**
* Adds the contextId to the list of bookmarked content.
* @param {string} contextId - id of the context
*/
PixLive.addBookmark = function(contextId) {
	exec(null, null, "PixLive", "addBookmark", [contextId]);
};

/**
* Removes the contextId from the list of bookmarked content.
* @param {string} contextId - id of the context
*/
PixLive.removeBookmark = function(contextId) {
	exec(null, null, "PixLive", "removeBookmark", [contextId]);
};

/**
* The callback success is called with true or false depending if the context ID is bookmarked or not.
* @param {string} contextId - id of the context
* @param {callback} success - success callback
* @param {callback} error - error callback
*/
PixLive.isBookmarked = function(contextId, success, error) {
	exec(success, error, "PixLive", "isBookmarked", [contextId]);
};

// Used to signal the plugin that the page is fully loaded
document.addEventListener("deviceready", function() {
	exec(null, null, "PixLive", "pageLoaded",  []);
}, false);

module.exports = PixLive;
