/********* PixLive.m Cordova Plugin Implementation *******/

#import "PixLive.h"
#import <Cordova/CDV.h>
#import <VDARSDK/VDARSDK.h>
#import "MyCameraImageSource.h"
#import "IonicARViewController.h"
#import "HolesView.h"

@implementation PixLive {
    HolesView *touchForwarder;
}

#pragma mark - Cordova methods

- (void)onAppTerminate {
    //Save SDK
    [[VDARSDKController sharedInstance] save];
}

- (void)onMemoryWarning {
    for(IonicARViewController * ctrl in [self.arViewControllers allValues]) {
        [ctrl didReceiveMemoryWarning];
    }
}

- (void)onReset {
    //Destroy all views
    for(NSNumber *key in [self.arViewControllers allKeys]) {
        
        IonicARViewController * ctrl = [self.arViewControllers objectForKey:key];
        
        if(ctrl.view.superview) {
            [ctrl viewWillDisappear:NO];
            [ctrl.view removeFromSuperview];
            [ctrl viewDidDisappear:NO];
        }
    }
    
    [self.arViewControllers removeAllObjects];
    [self.arViewSettings removeAllObjects];
}

#pragma mark - Plugin methods

-(CDVPlugin*) initWithWebView:(UIWebView*)theWebView
{
    self = [super initWithWebView:theWebView];
    
    _arViewControllers = [NSMutableDictionary dictionary];
    _arViewSettings = [NSMutableDictionary dictionary];
    
    self.webView.backgroundColor = [UIColor clearColor];
    self.webView.opaque = NO;
    
    [[NSNotificationCenter defaultCenter] addObserverForName:CDVPageDidLoadNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        self.webView.backgroundColor = [UIColor clearColor];
        self.webView.opaque = NO;
    }];
    
    return self;
}

-(void)setNotificationsSupport:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }

    if([arguments objectAtIndex:0] == [NSNull null]) {
        [VDARSDKController sharedInstance].enableNotifications=NO;
    } else {
        [VDARSDKController sharedInstance].enableNotifications=YES;
    }
}

-(void)disableTouch:(CDVInvokedUrlCommand *)command {
    touchForwarder.arTouchEnabled = NO;
}

-(void)enableTouch:(CDVInvokedUrlCommand *)command {
    touchForwarder.arTouchEnabled = YES;
}

-(void)beforeLeave:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl viewWillDisappear:NO];
}

-(void)afterLeave:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl.view removeFromSuperview];
    
    [ctrl viewDidDisappear:NO];
    
}

-(void)beforeEnter:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    NSDictionary * val = self.arViewSettings[[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    if(val) {
        CGRect r = [val[@"rect"] CGRectValue];
        ctrl.view.frame = r;
    }
    
    if(![val[@"insertBelow"] boolValue]) {
        [self.webView.superview addSubview:ctrl.view];
    } else {
        
        assert(touchForwarder);
        
        [touchForwarder.superview insertSubview:ctrl.view belowSubview:touchForwarder];
    }
    
    [ctrl viewWillAppear:NO];
    
    [ctrl.view setNeedsLayout];
}

-(void)afterEnter:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl viewDidAppear:NO];
    
}
-(void)init:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 2) {
        return;
    }
    
    NSURL *url = [NSURL URLWithString:arguments[0]];
    
    [VDARSDKController startSDK:[url path] withLicenseKey:arguments[1]];
    
    [VDARSDKController sharedInstance].enableCodesRecognition=YES;
    
    MyCameraImageSource *cameraSource=[[MyCameraImageSource alloc] init];
    
    [VDARSDKController sharedInstance].imageSender=cameraSource;
}

- (void) resize:(CDVInvokedUrlCommand *)command
{
    
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 5) { // at a minimum we need x origin, y origin and width...
        return;
    }
    
    CGFloat originx,originy,width, height;
    
    originx = [[arguments objectAtIndex:1] floatValue];
    originy = [[arguments objectAtIndex:2] floatValue];
    width = [[arguments objectAtIndex:3] floatValue];
    height = [[arguments objectAtIndex:4] floatValue];
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    
    CGRect viewRect = CGRectMake(
                                 originx,
                                 originy,
                                 width,
                                 height
                                 );
    
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    if(!ctrl) return;
    
    NSDictionary * original = self.arViewSettings[[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    NSDictionary * val = @{@"rect" : [NSValue valueWithCGRect:viewRect], @"insertBelow":original[@"insertBelow"]};
    
    self.arViewSettings[[NSNumber numberWithUnsignedInteger:ctrlID]] = val;
    
    if(ctrl.view.superview) {
        ctrl.view.frame = viewRect;
        
        
        UIInterfaceOrientation o = ctrl.interfaceOrientation;
        
        [ctrl willRotateToInterfaceOrientation:self.viewController.interfaceOrientation duration:0];
        [ctrl didRotateFromInterfaceOrientation:o];
    }
}


-(void)destroy:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    if(!ctrl) return;
    
    [ctrl.view removeFromSuperview];
    
    [self.arViewControllers removeObjectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [self.arViewSettings removeObjectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
}

-(void)presentViewController:(UIViewController *)viewControllerToPresent animated:(BOOL)flag completion:(void (^)(void))completion {
    [self.viewController presentViewController:viewControllerToPresent animated:flag completion:completion];
}

- (void) createARView:(CDVInvokedUrlCommand *)command
{
    
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 5) { // at a minimum we need x origin, y origin and width...
        return;
    }
    
    CGFloat originx,originy,width,height;
    
    originx = [[arguments objectAtIndex:0] floatValue];
    originy = [[arguments objectAtIndex:1] floatValue];
    width = [[arguments objectAtIndex:2] floatValue];
    height = [[arguments objectAtIndex:3] floatValue];
    
    NSUInteger ctrlID = [[arguments objectAtIndex:4] unsignedIntegerValue];
    
    CGRect viewRect = CGRectMake(
                                 originx,
                                 originy,
                                 width,
                                 height
                                 );
    
    IonicARViewController * ctrl = self.arViewControllers[[NSNumber numberWithUnsignedInteger:ctrlID]] = [[IonicARViewController alloc] initWithPlugin:self];
    
    [ctrl view]; //Load the view
    
    //Manually triggers the events
    [ctrl viewDidLoad];
    
    ctrl.view.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
    
    BOOL insertBelow;
    
    if(argc<6) {
        insertBelow = YES;
    } else {
        insertBelow = [arguments[5] boolValue];
    }
    
    NSDictionary * val = @{@"rect" : [NSValue valueWithCGRect:viewRect], @"insertBelow": [NSNumber numberWithBool:insertBelow] };
    
    if(insertBelow && !touchForwarder) {
        touchForwarder = [[HolesView alloc] initWithFrame:self.webView.frame andPlugin:self];
        touchForwarder.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        
        UIWebView *webView = self.webView;
        UIView *superview = webView.superview;
        
        [webView removeFromSuperview];
        
        [touchForwarder addSubview:webView];
        
        [superview addSubview:touchForwarder];
        
        touchForwarder.arTouchEnabled = YES;
    }
    
    self.arViewSettings[[NSNumber numberWithUnsignedInteger:ctrlID]] = val;
    
    
    if(!insertBelow) {
        [self.webView.superview addSubview:ctrl.view];
    } else {
        [touchForwarder.superview insertSubview:ctrl.view belowSubview:touchForwarder];
    }
    
    ctrl.view.frame = viewRect;
    
    [ctrl.view setNeedsLayout];
    
    [ctrl viewWillAppear:NO];
    
    [ctrl viewDidAppear:NO];
}

#pragma mark - Remote controller

- (void) synchronize:(CDVInvokedUrlCommand *)command
{
    
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    NSMutableArray *arrTags = [NSMutableArray arrayWithCapacity:argc];
    
    if(argc>0) {
        for(NSString * t in arguments[0]) {
            [arrTags addObject:[VDARTagPrior tagWithName:t]];
        }
    }
    
    [[VDARSDKController sharedInstance].afterLoadingQueue addOperationWithBlock:^{
        dispatch_async(dispatch_get_main_queue(), ^{
            [[VDARRemoteController sharedInstance] syncRemoteModelsAsynchronouslyWithPriors:arrTags withCompletionBlock:^(id result, NSError *err) {
                
                CDVPluginResult* pluginResult = nil;
                
                if (err==nil && result) {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:result];
                } else {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[err localizedDescription]];
                }
                
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                
            }];
        });
    }];
    
}


@end