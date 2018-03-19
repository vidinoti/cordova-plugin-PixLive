//
//  CordovaARViewController.h
//  myApp
//
//  Created by Mathieu Monney on 15.04.15.
//
//  Copyright 2010-2016 Vidinoti SA. All rights reserved.
//

#import <VDARSDK/VDARSDK.h>

@class PixLive;

@interface CordovaARViewController : VDARLiveAnnotationViewController

-(id)initWithPlugin:(PixLive*)plugin;

-(BOOL)captureScreenshot;

@end
