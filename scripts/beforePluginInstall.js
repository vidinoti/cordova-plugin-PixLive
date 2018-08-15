#!/usr/bin/env node

module.exports = function (context) {
	var fs = require('fs');
	var path = require('path');

	//If iOS and Android already exists, don't do anything
	if (fs.existsSync(context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive' + path.sep + 'VDARSDK.framework') &&
		fs.existsSync(context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive' + path.sep + 'vdarsdk-release.aar')) {
		return true;
	}

	var CommandParser = (function () {
		var parse = function (str, lookForQuotes) {
			var args = [];
			var readingPart = false;
			var part = '';
			for (var i = 0; i < str.length; i++) {
				if (str.charAt(i) === ' ' && !readingPart && str.charAt(i - 1) !== '\\') {
					args.push(part);
					part = '';
				} else {
					if (str.charAt(i) === '\"' && lookForQuotes) {
						readingPart = !readingPart;
					} else {
						part += str.charAt(i);
					}
				}
			}
			args.push(part);
			return args;
		};
		return {
			parse: parse
		};
	})();

	var Q = context.requireCordovaModule('q');
	var deferral = new Q.defer();




	var cmdLine = '"' + process.argv.join('" "') + '"';

	//We try to get the variable
	var args = CommandParser.parse(cmdLine, true);
	var variables = {};

	var argNamePatt = /((?:PIXLIVE_SDK_ANDROID_LOCATION)|(?:PIXLIVE_SDK_IOS_LOCATION))\s*=$/;
	var argNamePatt2 = /((?:PIXLIVE_SDK_ANDROID_LOCATION)|(?:PIXLIVE_SDK_IOS_LOCATION))\s*=(.+)/;

	for (var i = 0; i < args.length; i++) {
		var arg = args[i];

		if (arg == '--variable' && i + 1 < args.length) {
			var argName = args[i + 1];
			var res1 = argNamePatt2.exec(argName);

			i++;

			if (res1 && res1.length > 2) {
				variables[res1[1]] = res1[2];
			} else {
				var res2 = argNamePatt.exec(argName);
				if (res2 && res2.length > 1) {
					variables[res2[1]] = args[++i];
				}
			}


		}
	}

	var deleteFolderRecursive = function (path) {
		var files = [];
		if (fs.existsSync(path)) {
			files = fs.readdirSync(path);
			files.forEach(function (file, index) {
				var curPath = path + "/" + file;
				if (fs.lstatSync(curPath).isDirectory()) { // recurse
					deleteFolderRecursive(curPath);
				} else { // delete file
					fs.unlinkSync(curPath);
				}
			});
			fs.rmdirSync(path);
		}
	};

	if (!variables['PIXLIVE_SDK_IOS_LOCATION']) {
		console.error("You need to pass the variable PIXLIVE_SDK_IOS_LOCATION with the cordova plugin command line. E.g.: --variable PIXLIVE_SDK_IOS_LOCATION=\"path/to/VDARSDK.framework\"");
		console.error("Try using default location: ./PixLiveSDK/VDARSDK.framework");
		variables['PIXLIVE_SDK_IOS_LOCATION'] = './PixLiveSDK/VDARSDK.framework';
	}

	if (!variables['PIXLIVE_SDK_ANDROID_LOCATION']) {
		console.error("You need to pass the variable PIXLIVE_SDK_ANDROID_LOCATION with the cordova plugin command line. E.g.: --variable PIXLIVE_SDK_ANDROID_LOCATION=\"path/to/PixLive/vdarsdk-release.aar\"");
		console.error("Try using default location: ./PixLiveSDK/vdarsdk-release.aar");
		variables['PIXLIVE_SDK_ANDROID_LOCATION'] = './PixLiveSDK/vdarsdk-release.aar';
	}

	try {
		deleteFolderRecursive(context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive' + path.sep + 'VDARSDK.framework');
		deleteFolderRecursive(context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive' + path.sep + 'vdarsdk-release.aar');
	} catch (e) {

	}

	try {
		fs.mkdirSync(context.opts.plugin.dir + path.sep + 'vendor');
		fs.mkdirSync(context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive');
	} catch (e) {

	}

	var inFile = variables['PIXLIVE_SDK_IOS_LOCATION'];
	var outFile = context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive' + path.sep + 'VDARSDK.framework';

	var child = require("child_process");

	function copySync(from, to, callback) {
		from = from.replace(/"/gim, "\\\"");
		to = to.replace(/"/gim, "\\\"");
		child.exec("cp -r \"" + from + "\" \"" + to + "\"",
			function (error, stdout, stderr) {
				if (error !== null) {
					callback(error);
				} else {
					callback();
				}

			});
	}

	console.log('Copying PixLive SDK for iOS...');

	copySync(inFile, outFile, function (error) {
		if (error) {
			console.error("Copy error: " + error);
			throw new Error("Unable to copy VDARSDK.framework. Check the path of the PIXLIVE_SDK_IOS_LOCATION variable. Given: '" + inFile + "'");
		} else {
			console.log('Copying PixLive SDK for Android...');

			var inFile = variables['PIXLIVE_SDK_ANDROID_LOCATION'];

			var outFile = context.opts.plugin.dir + path.sep + 'vendor' + path.sep + 'PixLive';

			copySync(inFile, outFile, function (error) {
				if (error) {
					console.error("Copy error: " + error);
					throw new Error("Unable to copy PixLive SDK Android AAR library. Check the path of the PIXLIVE_SDK_ANDROID_LOCATION variable. Given: '" + inFile + "'");
				} else {
					deferral.resolve();
				}
			});
		}
	});

	return deferral.promise;
};