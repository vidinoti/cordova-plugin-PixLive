//
//  MyCameraImageSource.m
//  VDARSDK
//
//  Created by Mathieu Monney on 04.07.11.
//  Updated on 24.11.2014.
//
//  Copyright 2010-2014 Vidinoti SA. All rights reserved.
//

#import "MyCameraImageSource.h"
#import <QuartzCore/QuartzCore.h>

#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)

static NSString* imageForSimulatorCamera=@"http://armanager.vidinoti.com/images/beatles.jpg";



@interface UIImage (Resize)

- (UIImage *) resizedImageByMagick: (NSString *) spec;
- (UIImage *) resizedImageByWidth:  (NSUInteger) width;
- (UIImage *) resizedImageByHeight: (NSUInteger) height;
- (UIImage *) resizedImageWithMaximumSize: (CGSize) size;
- (UIImage *) resizedImageWithMinimumSize: (CGSize) size;
- (UIImage *) rotateImage:  (float) angle;

@end

#endif

@implementation MyCameraImageSource {
    UIInterfaceOrientation videoOrientation;
    
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    NSTimer *tmrImageDelivery;
    UIDeviceOrientation orientation;
    
    uint8_t *simulatorFramePlaneY;
    uint8_t *simulatorFramePlaneUV;
    size_t simulatorFramePlaneWidth[2];
    size_t simulatorFramePlaneHeight[2];
    size_t simulatorFramePlaneBytesPerRow[2];
    void *simulatorFramePlane[2];
    unsigned simulatorWidth,simulatorHeight;
    CVPixelBufferRef pixelBuffer;
    
    dispatch_queue_t dispatchQueue;
    
    BOOL blackFrame;
    BOOL loadingData;
#else
    AVCaptureSession *captureSession;
    dispatch_queue_t processingQueue;
    AVCaptureVideoDataOutput *videoOut;
    AVCaptureDevice *videoDevice;
    AVCaptureDeviceInput *videoIn;
    NSTimer *watchdogTimer;
    CFTimeInterval lastFrameTime;
#endif
    
    BOOL started;
}

@synthesize frameRate,imageReceiver;

#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)


static void releaseCallBack( void *releaseRefCon, const void *dataPtr, size_t dataSize, size_t numberOfPlanes, const void *planeAddresses[] ) {
    
}

-(BOOL)buildSimulatorImage {
    
    
    NSString *simDir=[[NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0] stringByAppendingPathComponent:@"simulatorImages"];
    
    
    
    NSString *pathNormal=[simDir stringByAppendingPathComponent:@"image.jpg"];
    NSString *pathRight=[simDir stringByAppendingPathComponent:@"image_right.jpg"];
    NSString *pathLeft=[simDir stringByAppendingPathComponent:@"image_left.jpg"];
    
    NSFileManager *fm = [[NSFileManager alloc] init];
    
    [fm createDirectoryAtPath:simDir withIntermediateDirectories:YES attributes:nil error:nil];
    
#if !__has_feature(objc_arc)
    [fm autorelease];
#endif
    
    if([fm fileExistsAtPath:pathNormal] && [fm fileExistsAtPath:pathRight] && [fm fileExistsAtPath:pathLeft]) {
        return YES;
    }
    
    if(loadingData) {
        return NO;
    }
    
    //Fetch the image
    //Dispatch async the get of the data
    
    loadingData=YES;
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
        
        NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:imageForSimulatorCamera]];
        
        if(data) {
            //Switch back to main thread to load image as this has to be loaded on main thread.
            dispatch_async(dispatch_get_main_queue(), ^{
                UIImage *img=[UIImage imageWithData:data];
                
                if(img) {
                    img=[img resizedImageByMagick:@"480x640*"];
                    
                    //Rotate it 90° anti-clockwise
                    UIImage *imgN=[img rotateImage:-M_PI/2.0f];
                    
                    [UIImageJPEGRepresentation(imgN, 1) writeToFile:pathNormal atomically:YES];
                    
                    
                    //Then scale it and crop it
                    // img=[img resizedImageByMagick:@"360x480"];
                    
                    UIImage *imgL=[img resizedImageByMagick:@"640x480*"];
                    
                    
                    
                    [UIImageJPEGRepresentation(imgL, 1) writeToFile:pathLeft atomically:YES];
                    
                    UIImage *imgR=[imgL rotateImage:M_PI];
                    
                    
                    [UIImageJPEGRepresentation(imgR, 1) writeToFile:pathRight atomically:YES];
                    
                    loadingData=NO;
                    
                    [self loadSimulatorFrame];
                    
                    return;
                }
            });
        }
        
    });
    
    return NO;
}

-(void)loadSimulatorFrame {
    
    
    if(blackFrame && simulatorFramePlaneY && simulatorFramePlaneUV) {
        bzero(simulatorFramePlaneY,simulatorWidth*simulatorHeight);
        bzero(simulatorFramePlaneUV,simulatorWidth*simulatorHeight/2);
        return;
    }
    
    if(![self buildSimulatorImage]) {
        return;
    }
    
    NSString *imgName;
    
    switch(orientation) {
        case UIDeviceOrientationLandscapeLeft:
            imgName = [NSString stringWithFormat:@"image_left.jpg"];
            break;
        case UIDeviceOrientationLandscapeRight:
            imgName = [NSString stringWithFormat:@"image_right.jpg"];
            break;
        default:
            imgName = [NSString stringWithFormat:@"image.jpg"];
    }
    
    NSString *simDir=[[NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0] stringByAppendingPathComponent:@"simulatorImages"];
    
    
    NSString *path=[simDir stringByAppendingPathComponent:imgName];
    
    UIImage *img=[UIImage imageWithContentsOfFile:path];
    
    if(!img) {
        NSAssert(0,@"Unable to find simulator image %@",path);
        return;
    }
    
    
    int imgSizeHeight= img.size.height;
    int imgSizeWidth= img.size.width;
    
    if(imgSizeWidth!=simulatorWidth || imgSizeHeight!=simulatorHeight) {
        
        
        if(pixelBuffer) {
            CVPixelBufferRelease(pixelBuffer);
            pixelBuffer=NULL;
        }
        
        if(simulatorFramePlaneY) {
            free(simulatorFramePlaneY);
            simulatorFramePlaneY=NULL;
        }
        
        
        if(simulatorFramePlaneUV) {
            free(simulatorFramePlaneUV);
            simulatorFramePlaneUV=NULL;
        }
        
        simulatorFramePlaneY=(uint8_t*)malloc(imgSizeWidth*imgSizeHeight);
        simulatorFramePlaneUV=(uint8_t*)malloc(imgSizeWidth*imgSizeHeight/2);
        
        simulatorWidth=imgSizeWidth;
        simulatorHeight=imgSizeHeight;
        
        
        simulatorFramePlaneWidth[0]=imgSizeWidth;
        simulatorFramePlaneHeight[0]=imgSizeHeight;
        simulatorFramePlaneWidth[1]=imgSizeWidth/2;
        simulatorFramePlaneHeight[1]=imgSizeHeight/2;
        simulatorFramePlaneBytesPerRow[0]=imgSizeWidth;
        simulatorFramePlaneBytesPerRow[1]=imgSizeWidth;
        
        simulatorFramePlane[0]=simulatorFramePlaneY;
        simulatorFramePlane[1]=simulatorFramePlaneUV;
        
        CVReturn ret=CVPixelBufferCreateWithPlanarBytes(NULL,imgSizeWidth,imgSizeHeight,kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,NULL,0,2,simulatorFramePlane,simulatorFramePlaneWidth,simulatorFramePlaneHeight,simulatorFramePlaneBytesPerRow,&releaseCallBack,
#if !__has_feature(objc_arc)
                                                        self
#else
                                                        (__bridge void *)(self)
#endif
                                                        ,NULL,&pixelBuffer);
        
        if(ret!=kCVReturnSuccess) {
            NSLog(@"Unable to create Pixel Buffer");
            pixelBuffer=NULL;
            return;
        }
        
    }
    
    
    
    
    if(blackFrame) {
        bzero(simulatorFramePlaneY,simulatorWidth*simulatorHeight);
        bzero(simulatorFramePlaneUV,simulatorWidth*simulatorHeight/2);
        return;
    } else {
        
        
        
        uint8_t *bmp=malloc(img.size.width*4*img.size.height);
        
        CGColorSpaceRef colorSpace=CGColorSpaceCreateDeviceRGB();
        
        CGContextRef context=CGBitmapContextCreate(bmp, (int)img.size.width, (int)img.size.height, 8, 4* (int)img.size.width, colorSpace, (CGBitmapInfo)kCGImageAlphaNoneSkipLast);
        
        CGColorSpaceRelease(colorSpace);
        
        
        CGContextDrawImage( context, CGRectMake( 0, 0, img.size.width,img.size.height ), img.CGImage );
        
        CGContextFlush(context);
        
        CGContextRelease(context);
        
        
        //Y Plane
        for(int y=0;y<imgSizeHeight;y++) {
            for(int x=0;x<imgSizeWidth;x++) {
                float r=bmp[y*(int)imgSizeWidth*4+x*4]/255.0f;
                float g=bmp[y*(int)imgSizeWidth*4+x*4+1]/255.0f;
                float b=bmp[y*(int)imgSizeWidth*4+x*4+2]/255.0f;
                
                float y2=0.299*r+0.587*g+0.114*b;
                //float u=-0.14713*r+-0.28886*g+0.436*b;
                //float v=0.615*r+-0.51499*g+-0.10001*b;
                
                simulatorFramePlaneY[y*((int)(imgSizeWidth))+x]=y2*255;
                
            }
        }
        
        //UV Plane
        for(int y=0;y<imgSizeHeight/2;y++) {
            for(int x=0;x<imgSizeWidth/2;x++) {
                
                float r1=bmp[2*(y)*(int)imgSizeWidth*4+2*(x)*4]/255.0f;
                float g1=bmp[2*(y)*(int)imgSizeWidth*4+2*(x)*4+1]/255.0f;
                float b1=bmp[2*(y)*(int)imgSizeWidth*4+2*(x)*4+2]/255.0f;
                
                float r2=bmp[2*(y)*(int)imgSizeWidth*4+(2*x+1)*4]/255.0f;
                float g2=bmp[2*(y)*(int)imgSizeWidth*4+(2*x+1)*4+1]/255.0f;
                float b2=bmp[2*(y)*(int)imgSizeWidth*4+(2*x+1)*4+2]/255.0f;
                
                float r3=bmp[(2*y+1)*(int)imgSizeWidth*4+2*(x)*4]/255.0f;
                float g3=bmp[(2*y+1)*(int)imgSizeWidth*4+2*(x)*4+1]/255.0f;
                float b3=bmp[(2*y+1)*(int)imgSizeWidth*4+2*(x)*4+2]/255.0f;
                
                float r4=bmp[(2*y+1)*(int)imgSizeWidth*4+(2*x+1)*4]/255.0f;
                float g4=bmp[(2*y+1)*(int)imgSizeWidth*4+(2*x+1)*4+1]/255.0f;
                float b4=bmp[(2*y+1)*(int)imgSizeWidth*4+(2*x+1)*4+2]/255.0f;
                
                float u1=-0.14713*r1+-0.28886*g1+0.436*b1;
                float v1=0.615*r1+-0.51499*g1+-0.10001*b1;
                
                float u2=-0.14713*r2+-0.28886*g2+0.436*b2;
                float v2=0.615*r2+-0.51499*g2+-0.10001*b2;
                
                float u3=-0.14713*r3+-0.28886*g3+0.436*b3;
                float v3=0.615*r3+-0.51499*g3+-0.10001*b3;
                
                float u4=-0.14713*r4+-0.28886*g4+0.436*b4;
                float v4=0.615*r4+-0.51499*g4+-0.10001*b4;
                
                assert(simulatorFramePlaneUV+y*((unsigned)(imgSizeWidth))+2*x<simulatorFramePlaneUV+simulatorHeight/2*simulatorWidth);
                assert(simulatorFramePlaneUV+y*((unsigned)(imgSizeWidth))+2*x+1<simulatorFramePlaneUV+simulatorHeight/2*simulatorWidth);
                simulatorFramePlaneUV[y*((int)(imgSizeWidth))+2*x]=((u1+u2+u3+u4)/4.0+0.436)*255;
                simulatorFramePlaneUV[y*((int)(imgSizeWidth))+2*x+1]=((v1+v2+v3+v4)/4.0+0.615)*255;
                
            }
        }
        free(bmp);
    }
}

#endif

#if !TARGET_IPHONE_SIMULATOR  && !defined(USE_FIXED_IMAGE)
-(BOOL)setupCamera {
    
    NSArray *allDevices=[AVCaptureDevice devices];
    
    for(AVCaptureDevice *dev in allDevices) {
        if(dev.position==AVCaptureDevicePositionBack) {
#if !__has_feature(objc_arc)
            [videoDevice release];
            videoDevice=[dev retain];
#else
            videoDevice=dev;
#endif
            break;
        }
    }
    
    
    if ( videoDevice ) {
        NSError *error;
        
        if([videoDevice lockForConfiguration:&error]) {
            if([videoDevice isFocusModeSupported:AVCaptureFocusModeContinuousAutoFocus])
                videoDevice.focusMode=AVCaptureFocusModeContinuousAutoFocus;
            if([videoDevice isWhiteBalanceModeSupported:AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance])
                videoDevice.whiteBalanceMode=AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance;
            if([videoDevice isExposureModeSupported:AVCaptureExposureModeContinuousAutoExposure])
                videoDevice.exposureMode=AVCaptureExposureModeContinuousAutoExposure;
            [videoDevice unlockForConfiguration];
        } else {
            NSLog(@"Error while locking the camera.");
        }
        
        
        
        videoIn = [AVCaptureDeviceInput deviceInputWithDevice:videoDevice error:&error];
#if !__has_feature(objc_arc)
        [videoIn retain];
#endif
        if ( !error ) {
            if ([captureSession canAddInput:videoIn])
                [captureSession addInput:videoIn];
            else{
                UIAlertView *a=[[UIAlertView alloc] initWithTitle:NSLocalizedString(@"Camera Error",@"") message:NSLocalizedString(@"Unable to connect to camera: Invalid input.",@"") delegate:nil cancelButtonTitle:NSLocalizedString(@"OK",@"") otherButtonTitles:nil];
                [a show];
#if !__has_feature(objc_arc)
                [a release];
#endif
                return NO;
            }
            self.frameRate=frameRate;
        }
        else{
            UIAlertView *a=[[UIAlertView alloc] initWithTitle:NSLocalizedString(@"Camera Error",@"") message:[NSString stringWithFormat:NSLocalizedString(@"Unable to connect to camera: %@.",@""),[error localizedDescription]] delegate:nil cancelButtonTitle:NSLocalizedString(@"OK",@"") otherButtonTitles:nil];
            [a show];
#if !__has_feature(objc_arc)
            [a release];
#endif
            return NO;
        }
    }
    else{
        UIAlertView *a=[[UIAlertView alloc] initWithTitle:NSLocalizedString(@"Camera Error",@"") message:NSLocalizedString(@"Unable to connect to camera: No camera found.",@"") delegate:nil cancelButtonTitle:NSLocalizedString(@"OK",@"") otherButtonTitles:nil];
        [a show];
#if !__has_feature(objc_arc)
        [a release];
#endif
        return NO;
    }
    return YES;
}


-(BOOL) isRunning {
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    return started==true;
#else
    return started && captureSession.running;
#endif
}

-(void)teardownCamera {
    [captureSession removeInput:videoIn];
#if !__has_feature(objc_arc)
    [videoIn release];
    [videoDevice release];
#endif
    
    videoDevice=nil;
    videoIn=nil;
}
-(void)setBlackFrame:(BOOL)_blackFrame {
    if(_blackFrame)
        [self stopImageStream];
    else
        [self startImageStream];
}
#else

-(void)orientationChange:(NSNotification*)notif {
    orientation=[UIDevice currentDevice].orientation;
    
    [self loadSimulatorFrame];
    
}

-(void)setBlackFrame:(BOOL)_blackFrame {
    blackFrame=_blackFrame;
    [self loadSimulatorFrame];
}

#endif

-(id)init {
    if((self = [super init])) {
        started=NO;
        
        //If we have an ipad, we will use a higher quality video and downscale it in live
        UIDevicePlatform plat= [VDARSDKController platformType];
        
#if !TARGET_IPHONE_SIMULATOR && !defined(USE_FIXED_IMAGE)
        captureSession = [[AVCaptureSession alloc] init];
        
        
        
        
        if([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            captureSession.sessionPreset = AVCaptureSessionPreset640x480; //For future use: AVCaptureSessionPreset1280x720
        } else {
            
            
            switch(plat) {
                case DeviceHardwareGeneralPlatform_iPhone_1G:
                case DeviceHardwareGeneralPlatform_iPhone_3G:
                case DeviceHardwareGeneralPlatform_iPhone_3GS:
                case DeviceHardwareGeneralPlatform_iPhone_4:
                case DeviceHardwareGeneralPlatform_iPod_Touch_1G:
                case DeviceHardwareGeneralPlatform_iPod_Touch_2G:
                case DeviceHardwareGeneralPlatform_iPod_Touch_3G:
                case DeviceHardwareGeneralPlatform_iPod_Touch_4G:
                case DeviceHardwareGeneralPlatform_iPad:
                    NSLog(@"Using camera resolution of 480x360.");
                    captureSession.sessionPreset = AVCaptureSessionPresetMedium; //To have a 480x360 picture
                    break;
                default:
                    NSLog(@"Using camera resolution of 640x480.");
                    captureSession.sessionPreset = AVCaptureSessionPreset640x480; //To have a 480x360 picture
                    
            }
            
        }
        
        
        processingQueue = dispatch_queue_create("com.MyCompany.MyCameraImageSource.ProcessingQueue", NULL);
        
        videoOut = [[AVCaptureVideoDataOutput alloc] init];
        videoOut.alwaysDiscardsLateVideoFrames = YES;
        
        [videoOut setVideoSettings:[NSDictionary dictionaryWithObject:[NSNumber numberWithInt:kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange] forKey:(id)kCVPixelBufferPixelFormatTypeKey]];
        
        [videoOut setSampleBufferDelegate:self queue:processingQueue];
        
        
        if ([captureSession canAddOutput:videoOut])
            [captureSession addOutput:videoOut];
        else{
            UIAlertView *a=[[UIAlertView alloc] initWithTitle:NSLocalizedString(@"Camera Error",@"") message:NSLocalizedString(@"Unable to initialize camera",@"") delegate:nil cancelButtonTitle:NSLocalizedString(@"OK",@"") otherButtonTitles:nil];
            [a show];
#if !__has_feature(objc_arc)
            [a release];
            [self release];
#endif
            return nil;
        }
        
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(cameraErrorDidOccured:) name:AVCaptureSessionRuntimeErrorNotification object:nil];
#endif
        
#if TARGET_IPHONE_SIMULATOR  || defined(USE_FIXED_IMAGE)
        dispatchQueue=dispatch_queue_create("CameraProcessingQueue", NULL);
        orientation=[UIDevice currentDevice].orientation;
        //Register for notification to rotate frame
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(orientationChange:) name:UIDeviceOrientationDidChangeNotification object:nil];
        
        //Load fixed frame
        [self loadSimulatorFrame];
#endif
        
        
        switch(plat) {
            case DeviceHardwareGeneralPlatform_iPhone_1G:
            case DeviceHardwareGeneralPlatform_iPhone_3G:
            case DeviceHardwareGeneralPlatform_iPhone_3GS:
            case DeviceHardwareGeneralPlatform_iPhone_4:
            case DeviceHardwareGeneralPlatform_iPod_Touch_1G:
            case DeviceHardwareGeneralPlatform_iPod_Touch_2G:
            case DeviceHardwareGeneralPlatform_iPod_Touch_3G:
            case DeviceHardwareGeneralPlatform_iPod_Touch_4G:
            case DeviceHardwareGeneralPlatform_iPad:
                self.frameRate=22;
                break;
            default:
                self.frameRate=30;
                
        }
        
        
    }
    return self;
}

#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)

-(void)tmrNewFrame {
    
    dispatch_async(dispatchQueue, ^{
        
        [imageReceiver didCaptureFrame:pixelBuffer atTimestamp:CACurrentMediaTime()];
        
    });
}
#else
- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection {
    
    CVImageBufferRef pixelBuffer = CMSampleBufferGetImageBuffer( sampleBuffer );
    
    [imageReceiver didCaptureFrame:pixelBuffer atTimestamp:CACurrentMediaTime()];
    lastFrameTime = CACurrentMediaTime();
}
#endif

-(void)setFrameRate:(unsigned int)f {
    
    NSAssert([NSThread mainThread]==[NSThread currentThread],@"setFrameRate: should be called on the main thread!");
    
    frameRate=f;
#if TARGET_IPHONE_SIMULATOR ||  defined(USE_FIXED_IMAGE)
    [tmrImageDelivery invalidate];
#if !__has_feature(objc_arc)
    [tmrImageDelivery release];
#endif
    tmrImageDelivery = [NSTimer timerWithTimeInterval:1.0/frameRate target:self selector:@selector(tmrNewFrame) userInfo:nil repeats:YES];
#if !__has_feature(objc_arc)
    [tmrImageDelivery retain];
#endif
    if(started) {
        [[NSRunLoop currentRunLoop] addTimer:tmrImageDelivery forMode:NSDefaultRunLoopMode];
    }
#else
   	AVCaptureConnection *conn = [videoOut connectionWithMediaType:AVMediaTypeVideo];
    if(conn) {
        
        AVCaptureDeviceFormat * bestFormat = nil;
        
        for ( AVFrameRateRange * range in videoDevice.activeFormat.videoSupportedFrameRateRanges ) {
            
            if(frameRate>=range.minFrameRate && frameRate<=range.maxFrameRate) {
                bestFormat = videoDevice.activeFormat;
                break;
            }
        }
        
        if(!bestFormat) {
            for ( AVCaptureDeviceFormat * format in [videoDevice formats] ) {
                for ( AVFrameRateRange * range in format.videoSupportedFrameRateRanges ) {
                    if(frameRate>=range.minFrameRate && frameRate<=range.maxFrameRate) {
                        bestFormat = format;
                        break;
                    }
                }
                if(bestFormat) {
                    break;
                }
            }
        }
        
        if(!bestFormat) {
            bestFormat = videoDevice.activeFormat;
        }
        
        if ( [videoDevice lockForConfiguration:NULL] == YES ) {
            if(![bestFormat isEqual:videoDevice.activeFormat]) {
                NSLog(@"Warning: changing format to %@",bestFormat);
                videoDevice.activeFormat = bestFormat;
            }
            
            videoDevice.activeVideoMinFrameDuration = CMTimeMake(1,frameRate);
            videoDevice.activeVideoMaxFrameDuration = CMTimeMake(1,frameRate);
            [videoDevice unlockForConfiguration];
        }
    }
#endif
}

-(void) cameraErrorDidOccured:(NSNotification*)notif {
    NSLog(@"Error while starting the camera: %@",[notif.userInfo objectForKey:AVCaptureSessionErrorKey]);
    if(started) {
        //Try to restart it a second later
        dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0f * NSEC_PER_SEC));
        dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
            [self startImageStream];
        });
    }
}

#if !TARGET_IPHONE_SIMULATOR && !defined(USE_FIXED_IMAGE)
-(void)watchdogTimer {
    CFTimeInterval deltaT = CACurrentMediaTime() - lastFrameTime;
    
    if(deltaT>2) {
        NSLog(@"Camera error: unable to receive frame from camera. Restarting...");
        [self stopImageStream];
        [self startImageStream];
    }
}
#endif

-(void)startImageStream {
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    if(started) return;
#else
    if(started && captureSession.running) return;
#endif
    
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    if(!tmrImageDelivery){
        tmrImageDelivery = [NSTimer timerWithTimeInterval:1.0/frameRate target:self selector:@selector(tmrNewFrame) userInfo:nil repeats:YES];
#if !__has_feature(objc_arc)
        [tmrImageDelivery retain];
#endif
    }
    [[NSRunLoop currentRunLoop] addTimer:tmrImageDelivery forMode:NSDefaultRunLoopMode];
#else
    [self setupCamera];
    [captureSession startRunning];
    
#if !__has_feature(objc_arc)
    [watchdogTimer release];
#endif
    
    lastFrameTime = 0;
    watchdogTimer = [NSTimer scheduledTimerWithTimeInterval:2 target:self selector:@selector(watchdogTimer) userInfo:nil repeats:YES];
    
#if !__has_feature(objc_arc)
    [watchdogTimer retain];
#endif
    
#endif
    started=YES;
}

-(void)stopImageStream {
    if(!started) return;
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    [tmrImageDelivery invalidate];
#if !__has_feature(objc_arc)
    [tmrImageDelivery release];
#endif
    tmrImageDelivery=nil;
#else
    
    [watchdogTimer invalidate];
#if !__has_feature(objc_arc)
    [watchdogTimer release];
#endif
    watchdogTimer=nil;
    
    [captureSession stopRunning];
    [self teardownCamera];
#endif
    started=NO;
}
-(void)dealloc {
    self.imageReceiver=nil;
    [self stopImageStream];
#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)
    [tmrImageDelivery invalidate];
#if !__has_feature(objc_arc)
    [tmrImageDelivery release];
#endif
    if(pixelBuffer)
        CVPixelBufferRelease(pixelBuffer);
    pixelBuffer=NULL;
    
    if(simulatorFramePlaneY)
        free(simulatorFramePlaneY);
    if(simulatorFramePlaneUV)
        free(simulatorFramePlaneUV);
    simulatorFramePlaneUV=NULL;
    simulatorFramePlaneY=NULL;
#if !__has_feature(objc_arc)
    dispatch_release(dispatchQueue);
#endif
    dispatchQueue=nil;
#else
    
#if !__has_feature(objc_arc)
    [captureSession release];
    [videoOut release];
    [videoDevice release];
    [videoIn release];
    
    [watchdogTimer release];
#endif
    
    [[NSNotificationCenter defaultCenter] removeObserver:self name:AVCaptureSessionRuntimeErrorNotification object:nil];
    
    captureSession=nil;
    
    processingQueue=nil;
    
    
    videoOut=nil;
    
    videoDevice=nil;
    
    videoIn=nil;
    
    watchdogTimer = nil;
#endif
    
#if !__has_feature(objc_arc)
    [super dealloc];
#endif
}

@end

#if TARGET_IPHONE_SIMULATOR || defined(USE_FIXED_IMAGE)

@implementation UIImage (Resize)

// width	Width given, height automagically selected to preserve aspect ratio.
// xheight	Height given, width automagically selected to preserve aspect ratio.
// widthxheight	Maximum values of height and width given, aspect ratio preserved.
// widthxheight^	Minimum values of width and height given, aspect ratio preserved.
// widthxheight!	Exact dimensions, no aspect ratio preserved.
// widthxheight#	Crop to this exact dimensions.

- (UIImage *) resizedImageByMagick: (NSString *) spec
{
    
    if([spec hasSuffix:@"!"]) {
        NSString *specWithoutSuffix = [spec substringToIndex: [spec length] - 1];
        NSArray *widthAndHeight = [specWithoutSuffix componentsSeparatedByString: @"x"];
        NSUInteger width = [[widthAndHeight objectAtIndex: 0] integerValue];
        NSUInteger height = [[widthAndHeight objectAtIndex: 1] integerValue];
        UIImage *newImage = [self resizedImageWithMinimumSize: CGSizeMake (width, height)];
        return [newImage drawImageInBounds: CGRectMake (0, 0, width, height)];
    }
    
    if([spec hasSuffix:@"#"]) {
        NSString *specWithoutSuffix = [spec substringToIndex: [spec length] - 1];
        NSArray *widthAndHeight = [specWithoutSuffix componentsSeparatedByString: @"x"];
        NSUInteger width = [[widthAndHeight objectAtIndex: 0] integerValue];
        NSUInteger height = [[widthAndHeight objectAtIndex: 1] integerValue];
        UIImage *newImage = [self resizedImageWithMinimumSize: CGSizeMake (width, height)];
        return [newImage croppedImageWithRect: CGRectMake ((newImage.size.width - width) / 2, (newImage.size.height - height) / 2, width, height)];
    }
    
    if([spec hasSuffix:@"*"]) {
        NSString *specWithoutSuffix = [spec substringToIndex: [spec length] - 1];
        NSArray *widthAndHeight = [specWithoutSuffix componentsSeparatedByString: @"x"];
        NSUInteger width = [[widthAndHeight objectAtIndex: 0] integerValue];
        NSUInteger height = [[widthAndHeight objectAtIndex: 1] integerValue];
        UIImage *newImage = [self resizedImageWithMaximumSize: CGSizeMake (width, height)];
        
        //If smaller than size, we put it in the center
        NSUInteger newWidth = MAX(width,newImage.size.width);
        NSUInteger newHeight = MAX(height,newImage.size.height);
        
        if(newWidth!=newImage.size.width || newHeight!=newImage.size.height) {
            
            return [newImage croppedImageWithRect: CGRectMake (-(newWidth-newImage.size.width) / 2, -(newHeight-newImage.size.height) / 2, newWidth, newHeight)];
            
        } else {
            return newImage;
        }
        
        return [newImage croppedImageWithRect: CGRectMake ((newImage.size.width - width) / 2, (newImage.size.height - height) / 2, width, height)];
    }
    
    if([spec hasSuffix:@"^"]) {
        NSString *specWithoutSuffix = [spec substringToIndex: [spec length] - 1];
        NSArray *widthAndHeight = [specWithoutSuffix componentsSeparatedByString: @"x"];
        return [self resizedImageWithMinimumSize: CGSizeMake ([[widthAndHeight objectAtIndex: 0] longLongValue],
                                                              [[widthAndHeight objectAtIndex: 1] longLongValue])];
    }
    
    NSArray *widthAndHeight = [spec componentsSeparatedByString: @"x"];
    if ([widthAndHeight count] == 1) {
        return [self resizedImageByWidth: [spec integerValue]];
    }
    if ([[widthAndHeight objectAtIndex: 0] isEqualToString: @""]) {
        return [self resizedImageByHeight: [[widthAndHeight objectAtIndex: 1] integerValue]];
    }
    return [self resizedImageWithMaximumSize: CGSizeMake ([[widthAndHeight objectAtIndex: 0] longLongValue],
                                                          [[widthAndHeight objectAtIndex: 1] longLongValue])];
}


- (CGImageRef) CGImageWithCorrectOrientation
{
    if (self.imageOrientation == UIImageOrientationDown) {
        //retaining because caller expects to own the reference
        CGImageRetain([self CGImage]);
        return [self CGImage];
    }
    UIGraphicsBeginImageContext(self.size);
    
    CGContextRef context = UIGraphicsGetCurrentContext();
    
    if (self.imageOrientation == UIImageOrientationRight) {
        CGContextRotateCTM (context, 90 * M_PI/180);
    } else if (self.imageOrientation == UIImageOrientationLeft) {
        CGContextRotateCTM (context, -90 * M_PI/180);
    } else if (self.imageOrientation == UIImageOrientationUp) {
        CGContextRotateCTM (context, 180 * M_PI/180);
    }
    
    [self drawAtPoint:CGPointMake(0, 0)];
    
    CGImageRef cgImage = CGBitmapContextCreateImage(context);
    UIGraphicsEndImageContext();
    
    return cgImage;
}


- (UIImage *) rotateImage:  (float) angle
{
    CGImageRef imgRef = [self CGImageWithCorrectOrientation];
    CGFloat original_width  = CGImageGetWidth(imgRef);
    CGFloat original_height = CGImageGetHeight(imgRef);
    
    CGAffineTransform xfrm = CGAffineTransformMakeRotation(angle);
    CGRect result = CGRectApplyAffineTransform (CGRectMake(0, 0, original_width, original_height), xfrm);
    
    CGImageRelease(imgRef);
    
    imgRef=nil;
    
    UIGraphicsBeginImageContext(CGSizeMake(result.size.width, result.size.height));
    
    CGContextRef context = UIGraphicsGetCurrentContext();
    
    // Move the origin to the middle of the image so we will rotate and scale around the center.
    CGContextTranslateCTM(context, result.size.width/2, result.size.height/2);
    
    CGContextRotateCTM (context, angle);
    
    [self drawAtPoint:CGPointMake(-self.size.width / 2, -self.size.height / 2)];
    
    UIImage *resizedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return resizedImage;
}



- (UIImage *) resizedImageByWidth:  (NSUInteger) width
{
    CGImageRef imgRef = [self CGImageWithCorrectOrientation];
    CGFloat original_width  = CGImageGetWidth(imgRef);
    CGFloat original_height = CGImageGetHeight(imgRef);
    CGFloat ratio = width/original_width;
    CGImageRelease(imgRef);
    return [self drawImageInBounds: CGRectMake(0, 0, width, round(original_height * ratio))];
}

- (UIImage *) resizedImageByHeight:  (NSUInteger) height
{
    CGImageRef imgRef = [self CGImageWithCorrectOrientation];
    CGFloat original_width  = CGImageGetWidth(imgRef);
    CGFloat original_height = CGImageGetHeight(imgRef);
    CGFloat ratio = height/original_height;
    CGImageRelease(imgRef);
    return [self drawImageInBounds: CGRectMake(0, 0, round(original_width * ratio), height)];
}

- (UIImage *) resizedImageWithMinimumSize: (CGSize) size
{
    CGImageRef imgRef = [self CGImageWithCorrectOrientation];
    CGFloat original_width  = CGImageGetWidth(imgRef);
    CGFloat original_height = CGImageGetHeight(imgRef);
    CGFloat width_ratio = size.width / original_width;
    CGFloat height_ratio = size.height / original_height;
    CGFloat scale_ratio = width_ratio > height_ratio ? width_ratio : height_ratio;
    CGImageRelease(imgRef);
    return [self drawImageInBounds: CGRectMake(0, 0, round(original_width * scale_ratio), round(original_height * scale_ratio))];
}

- (UIImage *) resizedImageWithMaximumSize: (CGSize) size
{
    CGImageRef imgRef = [self CGImageWithCorrectOrientation];
    CGFloat original_width  = CGImageGetWidth(imgRef);
    CGFloat original_height = CGImageGetHeight(imgRef);
    CGFloat width_ratio = size.width / original_width;
    CGFloat height_ratio = size.height / original_height;
    CGFloat scale_ratio = width_ratio < height_ratio ? width_ratio : height_ratio;
    CGImageRelease(imgRef);
    return [self drawImageInBounds: CGRectMake(0, 0, round(original_width * scale_ratio), round(original_height * scale_ratio))];
}

- (UIImage *) drawImageInBounds: (CGRect) bounds
{
    UIGraphicsBeginImageContext(bounds.size);
    [self drawInRect: bounds];
    UIImage *resizedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return resizedImage;
}

- (UIImage*) croppedImageWithRect: (CGRect) rect {
    
    UIGraphicsBeginImageContext(rect.size);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGRect drawRect = CGRectMake(-rect.origin.x, -rect.origin.y, self.size.width, self.size.height);
    CGContextClipToRect(context, CGRectMake(0, 0, rect.size.width, rect.size.height));
    [self drawInRect:drawRect];
    UIImage* subImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return subImage;
}


@end

#endif
