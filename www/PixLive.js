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

/**
* GPSPoint
* @class
*/
PixLive.GPSPoint = function(prop) {
	// Apply properties to the context object
	var keys = Object.keys(prop);
	for (var j = keys.length - 1; j >= 0; j--) {
		this[keys[j]] = prop[keys[j]];
	}
};

PixLive.GPSPoint.prototype = {

	/**
	* Returns the latitude of the GPS point
	*/
	getLat: function() {
		return this.lat;
	},

	/**
	* Returns the longitude of the GPS point
	*/
	getLon: function() {
		return this.lon;
	},

	/**
	* Returns the detection radius of the GPS point (a null value means unlimited)
	*/
	getDetectionRadius: function() {
		return this.detectionRadius;
	},

	/**
	* Returns the context ID of the context corresponding to the GPS point
	*/
	getContextId: function() {
		return this.contextId;
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
	},
	/**
	* Define a touch region where the touch event must not be intercepted. For example,
	* this is used for displaying a button for toggling the bookmark flag of a AR content.
	* The parameters define the bounding box of the "touch hole".
	* @param {integer} top - bounding box top (e.g. 0)
	* @param {integer} bottom - bounding box bottom (e.g. 100)
	* @param {integer} left - bounding box left (e.g. 0)
	* @param {integer} right - bounding box right (e.g. 100)
	*/
	setTouchHole: function(top, bottom, left, right) {
		exec(null, null, "PixLive", "setTouchHole", [top, bottom, left, right]);
	},

	/**
	 * Creates a screen capture of the currently displayed AR view and saves it in the
	 * device image gallery.
	 */
	captureScreenshot: function(success, error) {
		exec(success, error, "PixLive", "captureScreenshot", [this.handle]);
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
* Activate the notifactions support (disabled by default)
* @param {boolean} enabled - true to enable, false to disable
*/
PixLive.setNotificationsSupport = function( enabled, apiKey ) {
	exec(null, null, "PixLive", "setNotificationsSupport",  [ enabled ]);
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
* Synchronize the app with the linked PixLive Maker account http://pixlivemaker.com
* Use this method if you want to synchronize with tags and tours. If you need only tags, see the "synchronize" function.
*
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
* @param {number[]} tours - An array of tour ID to synchronize with.
* @param {callback} success - success callback
* @param {callback} error - error callback
*/
PixLive.synchronizeWithTours = function(tags, tours, success, error) {
	exec(success, error, "PixLive", "synchronize", [tags, tours]);
};

/**
* Synchronize the app with the linked PixLive Maker account http://pixlivemaker.com
* Use this method if you want to synchronize with tags, tours and contexts. If you need only tags, see the "synchronize" function.
*
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
* @param {number[]} tours - An array of tour ID to synchronize with.
* @param {string[]} contexts - An array of context ID to synchronize with.
* @param {callback} success - success callback
* @param {callback} error - error callback
*/
PixLive.synchronizeWithToursAndContexts = function(tags, tours, contexts, success, error) {
	exec(success, error, "PixLive", "synchronize", [tags, tours, contexts]);
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
 * Will show the list of "nearby" contents. It can be either geolocalized points (GPS points)
 * or beacons. If called with the coordinates (0, 0), a loading wheel (progress bar) will
 * be displayed for indicating that the position is being acquired. The list can then be
 * reloaded by calling the function PixLive.refreshNearbyList.
 * 
 * @param {float} latitude - the current latitude
 * @param {float} longitude - the current longitude
 */
PixLive.presentNearbyList = function (latitude, longitude) {

	if( (!latitude && latitude != 0) || (!longitude && longitude != 0) ) {
		exec(null, null, "PixLive", "presentNearbyList", []);
	} else {
		exec(null, null, "PixLive", "presentNearbyList", [latitude, longitude]);
	}
}

/**
 * If the list displaying the nearby GPS point is displayed, calling this function
 * will reload the nearby elements according to the new given coordinate.
 * The beacon list will be refreshed as well.
 * 
 * @param {float} latitude - the current latitude
 * @param {float} longitude - the current longitude
 */
PixLive.refreshNearbyList = function (latitude, longitude) {
	exec(null, null, "PixLive", "refreshNearbyList", [latitude, longitude]);
}

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
* The callback success is called with true or false depending if the app is containing GPS points or not
* @param {callback} success(list) - success callback with distance
* @param {callback} error - error callback
*/
PixLive.isContainingGPSPoints = function(success, error) {
	exec(success, error, "PixLive", "isContainingGPSPoints", []);
};

/**
* The callback success is called with the distance
* @param {Number} lat1 latitude of point 1
* @param {Number} lon1 longitude of point 1
* @param {Number} lat2 latitude of point 2
* @param {Number} lon2 longitude of point 2
* @param {callback} success(list) - success callback with distance
* @param {callback} error - error callback
*/
PixLive.computeDistanceBetweenGPSPoints = function(lat1, lon1, lat2, lon2, success, error) {
	exec(success, error, "PixLive", "computeDistanceBetweenGPSPoints", [lat1,lon1,lat2,lon2]);
};


/**
* Returns the list of contexts linked to nearby beacons
* @param {callback} success(list) - success callback with the list of contexts
* @param {callback} error - error callback
*/
PixLive.getNearbyBeacons = function(success, error) {
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
	}, error, "PixLive", "getNearbyBeacons", []);
};

/**
* The callback success is called with true or false depending if the app is containing Beacons or not
* @param {callback} success(isContainingBeacons) - success callback with boolean return value
* @param {callback} error - error callback
*/
PixLive.isContainingBeacons = function(success, error) {
	exec(success, error, "PixLive", "isContainingBeacons", []);
};

/**
* The callback success is called with the status
* @param {callback} success(status) - success callback with status
* @param {callback} error - error callback
*/
PixLive.getNearbyStatus = function(success, error) {
	exec(success, error, "PixLive", "getNearbyStatus", []);
};

/**
 * Enable or disable nearby requirement dialog
 * @param {bool} nearbyRequirementDialogEnabled - if enabled or not
 */
PixLive.setEnableNearbyRequirementDialog = function(nearbyRequirementDialogEnabled, success, error) {
	exec(null, null, "PixLive", "setEnableNearbyRequirementDialog", [nearbyRequirementDialogEnabled]);
};

/**
* Returns the list of nearby GPS points
* @param {Number} myLat current latitude
* @param {Number} myLon current longitude
* @param {callback} success(list) - success callback with the list of GPSPoint
* @param {callback} error - error callback
*/
PixLive.getNearbyGPSPoints = function(myLat, myLon, success, error) {
	exec(function(list) {
		if(success !== null) {
			var ret = [];
			for (var i = 0; i < list.length; i++) {
				var prop = list[i];
				var object = new PixLive.GPSPoint(prop);
				ret.push(object);
			}
			success(ret);
		}
	}, error, "PixLive", "getNearbyGPSPoints", [myLat,myLon]);
};

/**
* Returns the list of GPS points in the bounding box specified by its lower left and uper right corner
* @param {Number} latitude of the lower left corner
* @param {Number} longitude of the lower left corner
* @param {Number} latitude of the uper right corner
* @param {Number} longitude of the uper right corner
* @param {callback} success(list) - success callback with the list of GPSPoint
* @param {callback} error - error callback
*/
PixLive.getGPSPointsInBoundingBox = function(minLat, minLon, maxLat, maxLon, success, error) {
	exec(function(list) {
		if(success !== null) {
			var ret = [];
			for (var i = 0; i < list.length; i++) {
				var prop = list[i];
				var object = new PixLive.GPSPoint(prop);
				ret.push(object);
			}
			success(ret);
		}
	}, error, "PixLive", "getGPSPointsInBoundingBox", [minLat,minLon,maxLat,maxLon]);
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

/**
* Sets the language to use for online recognition
* @param {string} languageCode - the language to use (2-character code like "fr" or "en")
*/
PixLive.setCloudRecognitionLanguage = function(languageCode) {
	exec(null, null, "PixLive", "setCloudRecognitionLanguage",  [languageCode]);
};

// Used to signal the plugin that the page is fully loaded
document.addEventListener("deviceready", function() {
	exec(null, null, "PixLive", "pageLoaded",  []);
}, false);

module.exports = PixLive;
