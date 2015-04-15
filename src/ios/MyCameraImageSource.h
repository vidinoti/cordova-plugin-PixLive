//
//  MyCameraImageSource.h
//  VDARSDK
//
//  Created by Mathieu Monney on 04.07.11.
//  Updated on 13.02.2014.
//
//  Copyright 2010-2014 Vidinoti SA. All rights reserved.
//


#import <Foundation/Foundation.h>
#import <CoreMedia/CoreMedia.h>
#import <AVFoundation/AVFoundation.h>

#import <VDARSDK/VDARSDK.h>

#ifdef __APPLE__
#include "TargetConditionals.h"
#endif

@interface MyCameraImageSource : NSObject<VDARImageSender
#if !TARGET_IPHONE_SIMULATOR && !defined(USE_FIXED_IMAGE)
,AVCaptureVideoDataOutputSampleBufferDelegate
#endif
>

/** The receiver of the camera frames */
@property (nonatomic,assign) id<VDARImageReceiver> imageReceiver;

/**
 The frame rate of the camera.
 
 Can be adjusted to a lower value if the device is not powerful enough to keep up the framerate.
 Usually a value betweek 20-25 (fps) works correctly.
 */
@property (nonatomic) unsigned int frameRate;

@property (nonatomic, getter = isRunning, readonly) BOOL running;

/** Start the image stream */
-(void)startImageStream;

/** Strop the image stream */
-(void)stopImageStream;

-(void)setBlackFrame:(BOOL)blackFrame;
@end
