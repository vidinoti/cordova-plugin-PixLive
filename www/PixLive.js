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
		exec(null, null, "PixLive", "destroy", [this.handle, originx, originy, width, height]);
	}
};



PixLive.createARView = function( originx, originy, width, height ) {
	var handle = PixLive.nextViewHandle++;

    exec(null, null, "PixLive", "createARView",  [originx, originy, width, height, handle ]);
    return new PixLive(handle);
}

PixLive.init = function( storagePath, licenseKey ) {
	if(PixLive.nextViewHandle==0) {
   		ionic.EventController.on('resize', function() {
   			alert('resize');
   		}, window);
    }
	exec(null, null, "PixLive", "init",  [storagePath, licenseKey ]);
}

var PixLiveInstance = new PixLive();

module.exports = PixLive;
