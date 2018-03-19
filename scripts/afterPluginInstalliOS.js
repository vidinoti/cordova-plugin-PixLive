#!/usr/bin/env node

module.exports = function(context) {
	var fs = require('fs');
	var path = require('path');
	var xcode = require('./xcode/pbxProject');
	var exec = require('child_process').exec;

	function getProjectName(protoPath){
	  var content = fs.readFileSync('config.xml', 'utf-8');

	   	return /<name>([^<]+)<\/name>/.exec(content)[1];
	}

	function readStrings(file) {
		var json = {},
					strings = fs.readFileSync(file, 'ucs2'); // Read file source.

		// /^\\s*("(?:[^"]|\\\\")*"|[^"]\\w*)\\s*=\\s*("(?:[^"]|\\\\")*"|[^"]\\w*)\\s*;/
		strings.replace(/^\s*("(?:[^"]|\\")*"|[^"]\w*)\s*=\s*("(?:[^"]|\\")*"|[^"]\w*)\s*;\s*$/gm, function (w, name, value) { // convertion
			name = String(name || '').trim().replace(/^\s*"|\s*"$/g, '').trim();
			value = String(value || '').trim().replace(/^\s*"|\s*"$/g, '').trim();
			json[name] = value;
		});

		return json;
	}

	function writeStrings(loc, file) {
		var fd = fs.openSync(file, 'w');
		var k = Object.keys(loc);

		for(var i = 0; i < k.length; i++) {
			fs.writeSync(fd,  '"'+k[i].replace(/"/g,"\\\"")+'" = "'+loc[k[i]].replace(/"/g,"\\\"")+'";'+"\n",null,'utf-8');
		}

		fs.closeSync(fd);

		//Convert to UTF16

		exec('iconv -f UTF-8 -t UTF-16 "'+file+'" > '+file+'"', function(){});
	}


	function getXcodeProj(protoPath) {
		var files = fs.readdirSync(path.join(protoPath, 'platforms', 'ios'));
		for (var i = files.length - 1; i >= 0; i--) {
			if(path.extname(files[i])=='.xcodeproj') {
				return files[i];
			}
		}
		
		throw new Error('Unable to find the XCode project.');

		return null;
	}

	function getXCodeProjectDir(protoPath) {
		var files = fs.readdirSync(path.join(protoPath, 'platforms', 'ios'));
		for (var i = files.length - 1; i >= 0; i--) {

			if(fs.lstatSync(path.join(protoPath, 'platforms', 'ios',files[i])).isDirectory()) {
				var files2 = fs.readdirSync(path.join(protoPath, 'platforms', 'ios',files[i]));
				for (var j = files2.length - 1; j >= 0; j--) {
					if(files2[j]=='Plugins') {
						return files[i];
					}
				}
			}
		}

		throw new Error('Unable to find the XCode source dir.');

		return null;
	}


	var xcodeProjectName = getProjectName('.'),
		xcodeProjectPath = path.join('platforms', 'ios',getXCodeProjectDir('.'));


	var translationsInfoPlist = {
		'en': {
			"NSCameraUsageDescription":"$APP_NAME uses your camera to recognize articles & images.",
			"NSLocationAlwaysUsageDescription":"$APP_NAME tailors content based on your location.",
			"NSLocationUsageDescription":"$APP_NAME tailors content based on your location.",
			"NSLocationWhenInUseUsageDescription":"$APP_NAME tailors content based on your location.",
			"NSBluetoothPeripheralUsageDescription":"$APP_NAME tailors content based on your location.",
			"NSPhotoLibraryAddUsageDescription":"$APP_NAME saves camera captures to your photo library."
		},
		'fr': {
			"NSCameraUsageDescription": "$APP_NAME utilise votre caméra pour reconnaître les articles & images.",
			"NSLocationAlwaysUsageDescription": "$APP_NAME vous fournit du contenu personnalisé en utilisant votre position.",
			"NSLocationUsageDescription": "$APP_NAME vous fournit du contenu personnalisé en utilisant votre position.",
			"NSLocationWhenInUseUsageDescription": "$APP_NAME vous fournit du contenu personnalisé en utilisant votre position.",
			"NSBluetoothPeripheralUsageDescription": "$APP_NAME vous fournit du contenu personnalisé en utilisant votre position.",
			"NSPhotoLibraryAddUsageDescription":"$APP_NAME sauve vos captures d'écran dans votre gallerie."
		},
		'de': {
			"NSCameraUsageDescription": "$APP_NAME verwendet Ihre Kamera, um Artikel und Bilder zu erkennen.",
			"NSLocationAlwaysUsageDescription": "$APP_NAME liefert den passenden Inhalt zu Ihrem Standort.",
			"NSLocationUsageDescription": "$APP_NAME liefert den passenden Inhalt zu Ihrem Standort.",
			"NSLocationWhenInUseUsageDescription": "$APP_NAME liefert den passenden Inhalt zu Ihrem Standort.",
			"NSBluetoothPeripheralUsageDescription": "$APP_NAME liefert den passenden Inhalt zu Ihrem Standort.",
			"NSPhotoLibraryAddUsageDescription":"$APP_NAME saves camera captures to your photo library."
		},
		'nl': {
			"NSCameraUsageDescription": "$APP_NAME gebruikt uw camera om artikels en beelden te herkennen.",
			"NSLocationAlwaysUsageDescription": "$APP_NAME maakt gebruik van uw locatie om u gepersonaliseerde content aan te bieden.",
			"NSLocationUsageDescription": "$APP_NAME maakt gebruik van uw locatie om u gepersonaliseerde content aan te bieden.",
			"NSLocationWhenInUseUsageDescription": "$APP_NAME maakt gebruik van uw locatie om u gepersonaliseerde content aan te bieden.",
			"NSBluetoothPeripheralUsageDescription": "$APP_NAME maakt gebruik van uw locatie om u gepersonaliseerde content aan te bieden.",
			"NSPhotoLibraryAddUsageDescription":"$APP_NAME saves camera captures to your photo library."
		}
	};

	var translations = {
		'en': {
			"Loading...":"Loading...",
		},
		'fr': {
			"Loading...":"Chargement...",
		},
		'de': {
			"Loading...":"Laden...",
		},
		'nl': {
			"Loading...":"Het laden...",
		}
	};

	translationsInfoPlist['Base'] = translationsInfoPlist['en'];
	translations['Base'] = translations['en'];

	console.log('Adding translations in iOS project for Beacons...');

	var languages = Object.keys(translationsInfoPlist);

	if (typeof String.prototype.endsWith !== 'function') {
	    String.prototype.endsWith = function(suffix) {
	        return this.indexOf(suffix, this.length - suffix.length) !== -1;
	    };
	}

	//Replace the info.plist
	var files = fs.readdirSync(xcodeProjectPath);
	for(var i = 0;i<files.length;i++) {
		if(files[i].endsWith('-Info.plist')) {
			console.log('Changing app name in '+path.join(xcodeProjectPath,files[i]));
			var file = fs.readFileSync(path.join(xcodeProjectPath,files[i]), 'utf-8');

			file = file.replace(/\$APP_NAME/g,xcodeProjectName);
			var fd = fs.openSync(path.join(xcodeProjectPath,files[i]), 'w');
			fs.writeSync(fd,file);
			fs.closeSync(fd);
			break;
		}
	}

	for(var i = 0; i<languages.length; i++) {
		var f = path.join(xcodeProjectPath,languages[i]+'.lproj','InfoPlist.strings');
		console.log('path infoPlist.strings ' + f);

		var fLoc = path.join(xcodeProjectPath,languages[i]+'.lproj','Localizable.strings');
		try {
			fs.mkdirSync(path.join(xcodeProjectPath,languages[i]+'.lproj'));
			fs.closeSync(fs.openSync(f, 'w'));

			if(!fs.existsSync(fLoc))
				fs.closeSync(fs.openSync(fLoc, 'w'));
		} catch(e) {

		}

		//Read the InfoPlist file and parse
		var loc = readStrings(f);

		var locStrings = Object.keys(translationsInfoPlist[languages[i]]);

		for(var j = 0; j < locStrings.length; j++) {
			loc[locStrings[j]] = translationsInfoPlist[languages[i]][locStrings[j]].replace(/\$APP_NAME/g,xcodeProjectName);
		}

		//Write it back
		writeStrings(loc,f);

		//Read the Localizable file and parse
		loc = readStrings(fLoc);

		locStrings = Object.keys(translations[languages[i]]);

		for(var j = 0; j < locStrings.length; j++) {
			loc[locStrings[j]] = translations[languages[i]][locStrings[j]];
		}

		//Write it back
		writeStrings(loc,fLoc);
	}

	var xcodeProjectPath2 = path.join('platforms', 'ios',getXcodeProj('.'),'project.pbxproj');
	var xcodeProject = xcode(xcodeProjectPath2);

	xcodeProject.parse(function(err){
		if(err){
		  console.log('An error occured during parsing of [' + xcodeProjectPath2 + ']: ' + JSON.stringify(err));
		}else{
			console.log('Adding files to Xcode project...');

			var uuidInfoPlist = xcodeProject.generateUuid();
			var uuidInfoPlistFileSection = xcodeProject.generateUuid();

			var uuidLocalizable = xcodeProject.generateUuid();
			var uuidLocalizableFileSection = xcodeProject.generateUuid();
			
			xcodeProject.pbxVariantGroup()[uuidInfoPlist] = {
				isa: 'PBXVariantGroup',
				children: [],
				name: 'InfoPlist.strings',
				path: '../..',
				sourceTree: '"<group>"'
			};

			xcodeProject.pbxVariantGroup()[uuidLocalizable] = {
				isa: 'PBXVariantGroup',
				children: [],
				name: 'Localizable.strings',
				path: '../..',
				sourceTree: '"<group>"'
			};


			xcodeProject.pbxBuildFileSection()[uuidInfoPlistFileSection] = {
				isa: 'PBXBuildFile',
				fileRef: uuidInfoPlist
			};

			xcodeProject.pbxBuildFileSection()[uuidLocalizableFileSection] = {
				isa: 'PBXBuildFile',
				fileRef: uuidLocalizable
			};

			xcodeProject.pbxBuildFileSection()[uuidInfoPlistFileSection+'_comment'] = 'InfoPlist.strings in Resources';
			xcodeProject.pbxBuildFileSection()[uuidLocalizableFileSection+'_comment'] = 'Localizable.strings in Resources';

			for(var i = 0; i<languages.length; i++) {
				var uuidLanguage = xcodeProject.generateUuid();
			
    			xcodeProject.pbxFileReferenceSection()[uuidLanguage] = {
    				isa: 'PBXFileReference',
    				lastKnownFileType: 'text.plist.strings',
    				name: languages[i],
    				path: '"'+getXCodeProjectDir('.')+'/'+languages[i]+'.lproj/InfoPlist.strings"',
    				sourceTree: '"<group>"'
    			};

    			xcodeProject.pbxFileReferenceSection()[uuidLanguage+'_comment'] = languages[i];

				xcodeProject.pbxVariantGroup()[uuidInfoPlist]['children'].push({'value': uuidLanguage,'comment': languages[i]});

				var uuidLanguage2 = xcodeProject.generateUuid();
			
    			xcodeProject.pbxFileReferenceSection()[uuidLanguage2] = {
    				isa: 'PBXFileReference',
    				lastKnownFileType: 'text.plist.strings',
    				name: languages[i],
    				path: '"'+getXCodeProjectDir('.')+'/'+languages[i]+'.lproj/Localizable.strings"',
    				sourceTree: '"<group>"'
    			};

    			xcodeProject.pbxFileReferenceSection()[uuidLanguage2+'_comment'] = languages[i];

				xcodeProject.pbxVariantGroup()[uuidLocalizable]['children'].push({'value': uuidLanguage2,'comment': languages[i]});

			}

			xcodeProject.pbxGroupByName('Resources').children.push({'value': uuidInfoPlist,'comment': 'InfoPlist.strings'});
			xcodeProject.pbxGroupByName('Resources').children.push({'value': uuidLocalizable,'comment': 'Localizable.strings'});

			xcodeProject.pbxVariantGroup()[uuidInfoPlist+'_comment'] = 'InfoPlist.strings';
			xcodeProject.pbxVariantGroup()[uuidLocalizable+'_comment'] = 'Localizable.strings';

			xcodeProject.pbxResourcesBuildPhaseObj(Object.keys(xcodeProject.pbxNativeTargetSection())[0]).files.push({'value': uuidLocalizableFileSection,'comment': 'Localizable.strings in Resources'});
			xcodeProject.pbxResourcesBuildPhaseObj(Object.keys(xcodeProject.pbxNativeTargetSection())[0]).files.push({'value': uuidInfoPlistFileSection,'comment': 'InfoPlist.strings in Resources'});

			fs.writeFileSync(xcodeProjectPath2, xcodeProject.writeSync(), 'utf-8');

			console.log('[' + xcodeProjectPath2 + '] now has translations for languages: '+languages.toString());
		}
	  });
};