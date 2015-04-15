//
//  IonicARViewController.h
//  myApp
//
//  Created by Mathieu Monney on 15.04.15.
//
//

#import <VDARSDK/VDARSDK.h>

@class PixLive;

@interface IonicARViewController : VDARLiveAnnotationViewController

-(id)initWithPlugin:(PixLive*)plugin;

@end
