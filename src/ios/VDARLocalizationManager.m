//
//  VDARLocalizationManager.m
//  VDARSDK
//
//  Created by Laurent Rime on 5/30/11.
//
//  Copyright 2011-2016 Vidinoti SA. All rights reserved.
//

#import "VDARLocalizationManager.h"
#import <VDARSDK/VDARSDK.h>

#define MIN_DISTANCE_UPDATE 10 // in meters. Minimum movement to update GPS position

// The radius used by Vidinoti servers to search for AR Model around the given GPS position (in meters)
#define SEARCH_RADIUS 500

static dispatch_once_t pred;
static id shared = nil;

@implementation VDARLocalizationManager

@synthesize localizationPrior,hasPositionForVDARSDK,positionPrecision;


- (id)initPrivate {
    if((self=[super init])) {
        hasPositionForVDARSDK=NO;
        positionSimulationSwitch=0;
        timerPosition = nil;
        positionPrecision=1e10;
		
        localizationPrior = [[VDARLocalizationPrior alloc] init];

        locationManager = [[CLLocationManager alloc] init];
        locationManager.delegate = self;
        locationManager.distanceFilter = MIN_DISTANCE_UPDATE;
        locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters; // 10 m

        if ([[CLLocationManager class] respondsToSelector:@selector(requestWhenInUseAuthorization)]) {
            CLAuthorizationStatus status = [CLLocationManager authorizationStatus];
            
            if (status == kCLAuthorizationStatusNotDetermined) {
                [locationManager requestWhenInUseAuthorization];
            }
        }

        [locationManager startUpdatingLocation];
    }
    
    return self;
}

-(void)dealloc {
	[locationManager stopUpdatingLocation];
	locationManager=nil;
}


- (void)locationManager:(CLLocationManager *)manager
    didUpdateToLocation:(CLLocation *)newLocation
           fromLocation:(CLLocation *)oldLocation
{
    float latitude = newLocation.coordinate.latitude;
    float longitude = newLocation.coordinate.longitude;
    
    
	
	localizationPrior.latitude = latitude;
	localizationPrior.longitude = longitude;
	localizationPrior.searchDistance=MAX(SEARCH_RADIUS,newLocation.horizontalAccuracy);
	hasPositionForVDARSDK=YES;
	positionPrecision=newLocation.horizontalAccuracy;
}



#pragma mark -
#pragma mark Singleton methods

+(void)startManager {
    dispatch_once(&pred, ^{
        shared = [[super alloc] initPrivate];
    });
}

+ (VDARLocalizationManager*)sharedInstance
{
    return shared;
}



@end
