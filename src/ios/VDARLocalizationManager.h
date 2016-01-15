//
//  VDARLocalizationManager.h
//  VDARSDK
//
//  Created by Laurent Rime on 5/30/11.
//  Copyright 2011 Vidinoti SA. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

@class VDARLocalizationPrior;

@interface VDARLocalizationManager : NSObject <CLLocationManagerDelegate>{
    
    CLLocationManager *locationManager;
    
    int positionSimulationSwitch; // for position simulation

    NSTimer * timerPosition;
}

@property (nonatomic,strong) VDARLocalizationPrior * localizationPrior;
@property (nonatomic) BOOL hasPositionForVDARSDK;
@property (nonatomic,readonly) float positionPrecision;

+ (VDARLocalizationManager*)sharedInstance;
+ (void)startManager;

// clue for improper use (produces compile time error)
+(instancetype) alloc __attribute__((unavailable("alloc not available, call sharedInstance instead")));
-(instancetype) init __attribute__((unavailable("init not available, call sharedInstance instead")));
+(instancetype) new __attribute__((unavailable("new not available, call sharedInstance instead")));


@end
