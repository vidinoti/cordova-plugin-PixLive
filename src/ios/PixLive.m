//
//  PixLive.m
//  PixLive SDK Cordova plugin
//
//  Created by Mathieu Monney on 15.04.15.
//
//  Copyright 2010-2016 Vidinoti SA. All rights reserved.
//

#import "PixLive.h"
#import <Cordova/CDV.h>
#import "CordovaARViewController.h"
#import "HolesView.h"
#import "AppDelegate.h"
#import "VDARLocalizationManager.h"

@interface AppDelegate (VDARAPPRegisterUserNotificationSettings)

// Tells the delegate what types of notifications may be used
- (void)                    application:(UIApplication*)application
    didRegisterUserNotificationSettings:(UIUserNotificationSettings*)settings;

@end


static NSString* const VDARApplicationRegisterUserNotificationSettings = @"UIApplicationRegisterUserNotificationSettings";

@implementation AppDelegate (VDARAPPRegisterUserNotificationSettings)

/**
 * Tells the delegate what types of notifications may be used
 * to get the user’s attention.
 */
- (void) application:(UIApplication*)application
    didRegisterUserNotificationSettings:(UIUserNotificationSettings*)settings
{
    NSNotificationCenter* center = [NSNotificationCenter
                                    defaultCenter];
    
    // re-post (broadcast)
    [center postNotificationName:VDARApplicationRegisterUserNotificationSettings
                          object:settings];
}


- (void)application:(UIApplication *)application
didReceiveRemoteNotification:(NSDictionary *)userInfo {
    if(application.applicationState==UIApplicationStateInactive || application.applicationState==UIApplicationStateActive) {
        [[VDARSDKController sharedInstance] application:application didReceiveRemoteNotification:userInfo];
    }
}

- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forRemoteNotification:(NSDictionary *)userInfo completionHandler:(void(^)())completionHandler
{
    [[VDARSDKController sharedInstance] application:application handleActionWithIdentifier:identifier forRemoteNotification:userInfo completionHandler:completionHandler];
}

@end


@implementation PixLive {
    HolesView *touchForwarder;
    NSString *eventCallbackId;
    BOOL pageLoaded;
    NSOperationQueue *foregroundOperationQueue; //Queue containing tasks that are ran only when app is loaded and ready
}

#pragma mark - Cordova methods

- (void)onAppTerminate {
    //Save SDK
    [[VDARSDKController sharedInstance] save];
}

- (void)onMemoryWarning {
    for(CordovaARViewController * ctrl in [self.arViewControllers allValues]) {
        [ctrl didReceiveMemoryWarning];
    }
}

- (void)onReset {
    //Destroy all views
    for(NSNumber *key in [self.arViewControllers allKeys]) {
        
        CordovaARViewController * ctrl = [self.arViewControllers objectForKey:key];
        
        if(ctrl.view.superview) {
            [ctrl viewWillDisappear:NO];
            [ctrl.view removeFromSuperview];
            [ctrl viewDidDisappear:NO];
        }
    }
    
    [self.arViewControllers removeAllObjects];
    [self.arViewSettings removeAllObjects];
    
    pageLoaded = NO;
}

- (void)dispose
{
    //Destroy all views
    for(NSNumber *key in [self.arViewControllers allKeys]) {
        
        CordovaARViewController * ctrl = [self.arViewControllers objectForKey:key];
        
        if(ctrl.view.superview) {
            [ctrl viewWillDisappear:NO];
            [ctrl.view removeFromSuperview];
            [ctrl viewDidDisappear:NO];
        }
    }
    
    [self.arViewControllers removeAllObjects];
    [self.arViewSettings removeAllObjects];

    [[VDARSDKController sharedInstance].detectionDelegates removeObject:self];
    [VDARRemoteController sharedInstance].delegate=nil;

    [touchForwarder removeFromSuperview];
    touchForwarder=nil;
    
    pageLoaded = NO;
}

-(void)dealloc {
    [touchForwarder removeFromSuperview];
    touchForwarder=nil;
}


#pragma mark - Notifications methods

- (void)application:(UIApplication *)app didFailToRegisterForRemoteNotificationsWithError:(NSError *)err {
    [[VDARSDKController sharedInstance] application:app didFailToRegisterForRemoteNotificationsWithError:err];
}

- (void)didReceiveLocalNotification:(NSNotification *)notification {
    [[VDARSDKController sharedInstance] application:[UIApplication sharedApplication] didReceiveLocalNotification:notification.object];
}

#pragma mark - Plugin methods

-(NSData*)dataWithHexString:(NSString *)hex
{
    char buf[3];
    buf[2] = '\0';
    NSAssert(0 == [hex length] % 2, @"Hex strings should have an even number of digits (%@)", hex);
    unsigned char *bytes = malloc([hex length]/2);
    unsigned char *bp = bytes;
    for (CFIndex i = 0; i < [hex length]; i += 2) {
        buf[0] = [hex characterAtIndex:i];
        buf[1] = [hex characterAtIndex:i+1];
        char *b2 = NULL;
        *bp++ = strtol(buf, &b2, 16);
        NSAssert(b2 == buf + 2, @"String should be all hex digits: %@ (bad digit around %ld)", hex, i);
    }
    
    return [NSData dataWithBytesNoCopy:bytes length:[hex length]/2 freeWhenDone:YES];
}

-(CDVPlugin*) initWithWebView:(UIWebView*)theWebView
{
    self = [super initWithWebView:theWebView];
    
    _arViewControllers = [NSMutableDictionary dictionary];
    _arViewSettings = [NSMutableDictionary dictionary];

    foregroundOperationQueue = [[NSOperationQueue alloc] init];
    [foregroundOperationQueue setMaxConcurrentOperationCount:1];

    [foregroundOperationQueue setSuspended:YES];
    
    self.webView.backgroundColor = [UIColor clearColor];
    self.webView.opaque = NO;

    NSDictionary* infoDict = [[NSBundle mainBundle] infoDictionary];

    NSString* apiKey = [infoDict objectForKey:@"PixLiveLicense"];

    if(!apiKey || apiKey.length==0) {
        [NSException raise:@"No API Key in Info.plist for PixLive SDK" format:@"No API Key in Info.plist for PixLive SDK"];
    }

    NSString *modelDir=[[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0] stringByAppendingPathComponent:@"pixliveSDK"];
    
    [VDARSDKController startSDK:modelDir withLicenseKey:apiKey];
    
    [VDARSDKController sharedInstance].enableCodesRecognition=YES;
    
    [VDARSDKController sharedInstance].imageSender = [[VDARCameraImageSource alloc] init];
    
    [[VDARSDKController sharedInstance].detectionDelegates addObject:self];
    
    [[NSNotificationCenter defaultCenter] addObserverForName:CDVPageDidLoadNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        self.webView.backgroundColor = [UIColor clearColor];
        self.webView.opaque = NO;
    }];


    [[NSNotificationCenter defaultCenter] addObserverForName:CDVRemoteNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        NSData *d = [self dataWithHexString:note.object];
        
        [[VDARSDKController sharedInstance] application:[UIApplication sharedApplication] didRegisterForRemoteNotificationsWithDeviceToken:d];
    }];

    [[NSNotificationCenter defaultCenter] addObserverForName:CDVRemoteNotificationError object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        [[VDARSDKController sharedInstance] application:[UIApplication sharedApplication] didFailToRegisterForRemoteNotificationsWithError:note.object];
    }];

    [[NSNotificationCenter defaultCenter] addObserverForName:VDARApplicationRegisterUserNotificationSettings object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    }];
    
    [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidFinishLaunchingNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *notif) {
        NSDictionary *userInfo = notif.userInfo;
        
        if([userInfo objectForKey:UIApplicationLaunchOptionsRemoteNotificationKey]) {
            [[VDARSDKController sharedInstance] application:[UIApplication sharedApplication] didReceiveRemoteNotification:[userInfo objectForKey:UIApplicationLaunchOptionsRemoteNotificationKey]];
        }
    }];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(didReceiveLocalNotification:) name:CDVLocalNotification object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserverForName:VDARApplicationRegisterUserNotificationSettings object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    }];

    [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidBecomeActiveNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        if(pageLoaded) {
            [foregroundOperationQueue setSuspended:NO];
        }
    }];

    [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationWillResignActiveNotification object:nil queue:[NSOperationQueue currentQueue] usingBlock:^(NSNotification *note) {
        [foregroundOperationQueue setSuspended:YES];
    }];

    [VDARRemoteController sharedInstance].delegate=self;

    [VDARLocalizationManager sharedInstance];
    
    return self;
}

-(void)pageLoaded:(CDVInvokedUrlCommand *)command {
    if([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive) {
        [foregroundOperationQueue setSuspended:NO];
        pageLoaded = YES;
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
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl viewWillDisappear:NO];
}

-(void)afterLeave:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
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
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
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
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl viewDidAppear:NO];
    
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

-(void)installEventHandler:(CDVInvokedUrlCommand *)command {
    eventCallbackId = command.callbackId;
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
    
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
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
    CordovaARViewController * ctrl = [self.arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    if(!ctrl) return;

    [ctrl viewWillDisappear:NO];
    [ctrl.view removeFromSuperview];
    [ctrl viewDidDisappear:NO];

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
    
    CordovaARViewController * ctrl = self.arViewControllers[[NSNumber numberWithUnsignedInteger:ctrlID]] = [[CordovaARViewController alloc] initWithPlugin:self];
    
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

- (void) presentNotificationsList:(CDVInvokedUrlCommand *)command
{
    
    NSUInteger l = [[VDARSDKController sharedInstance].pendingNotifications count];
    
    CDVPluginResult* pluginResult = nil;
    if (l == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"empty"];
    } else {
        [[VDARSDKController sharedInstance] presentNotificationsList];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) getContexts:(CDVInvokedUrlCommand *)command
{
    NSArray *contextIds = [[VDARSDKController sharedInstance] contextIDs];
    NSMutableArray *output = [NSMutableArray array];

    for(NSString* ctxId in contextIds) {
        VDARContext * c  = [[VDARSDKController sharedInstance] getContext:ctxId];
        if(c) {

            NSDictionary *dict = @{
                                   @"contextId": ctxId,
                                   @"name": c.name ? c.name : [NSNull null],
                                   @"lastUpdate": c.lastmodif ? [c.lastmodif descriptionWithLocale:nil] : [NSNull null],
                                   @"description": c.contextDescription ? c.contextDescription : [NSNull null],
                                   @"notificationTitle": c.notificationTitle ? c.notificationTitle : [NSNull null],
                                   @"notificationMessage":  c.notificationMessage ? c.notificationMessage : [NSNull null],
                                   @"imageThumbnailURL": c.imageThumbnailURL.absoluteString,
                                   @"imageHiResURL": c.imageHiResURL.absoluteString,
                                   };

            [output addObject:dict];
        }
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:output];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}


- (void) activateContext:(CDVInvokedUrlCommand *)command
{
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if(argc>0 && [arguments[0] isKindOfClass:[NSString class]]) {
        VDARContext * c  = [[VDARSDKController sharedInstance] getContext:arguments[0]];
        if(c) {
            [c activate];
        }
    }
}

- (void) ignoreContext:(CDVInvokedUrlCommand *)command
{
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if(argc>0 && [arguments[0] isKindOfClass:[NSString class]]) {
        VDARContext * c  = [[VDARSDKController sharedInstance] getContext:arguments[0]];
        if(c) {
            [c ignore];
        }
    }
}


- (void) openURLInInternalBrowser:(CDVInvokedUrlCommand *)command
{
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if(argc>0 && [arguments[0] isKindOfClass:[NSString class]]) {
        NSURL * url = [NSURL URLWithString:arguments[0]];
        if(url) {
            [[VDARSDKController sharedInstance] openURLInInternalBrowser:url];
        }
    }
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

-(void)remoteController:(VDARRemoteController*)controller didProgress:(float)prc isReady:(bool)isReady folder:(NSString*)folder {
    if(eventCallbackId) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"syncProgress", @"progress": [NSNumber numberWithFloat:prc]}];
        
        pluginResult.keepCallback = @YES;
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
    }
}

#pragma mark - PixLive SDK Delegate

-(NSString*)codeTypeAsString:(VDARCodeType)t {
    switch(t) {
        case VDAR_CODE_TYPE_NONE      :   return @"none";
        case VDAR_CODE_TYPE_EAN2      :   return @"ean2";
        case VDAR_CODE_TYPE_EAN5      :   return @"ean5";
        case VDAR_CODE_TYPE_EAN8      :   return @"ean8";
        case VDAR_CODE_TYPE_UPCE      :   return @"upce";
        case VDAR_CODE_TYPE_ISBN10    :   return @"isbn10";
        case VDAR_CODE_TYPE_UPCA      :   return @"upca";
        case VDAR_CODE_TYPE_EAN13     :   return @"ean13";
        case VDAR_CODE_TYPE_ISBN13    :   return @"isbn13";
        case VDAR_CODE_TYPE_COMPOSITE :   return @"composite";
        case VDAR_CODE_TYPE_I25       :   return @"i25";
        case VDAR_CODE_TYPE_CODE39    :   return @"code39";
        case VDAR_CODE_TYPE_QRCODE    :   return @"qrcode";
    }
}

-(void)codesDetected:(NSArray *)codes {
    if(eventCallbackId) {
        for (VDARCode* c in codes) {
            if(!c.isSpecialCode) {
                CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"codeRecognize", @"code": c.codeData, @"codeType": [self codeTypeAsString:c.codeType]}];
                
                pluginResult.keepCallback = @YES;
                
                [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
            }
        }
    }
}

-(void)didEnterContext:(VDARContext *)context {
    if(eventCallbackId) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"enterContext", @"context": context.remoteID ? context.remoteID : @""}];

        pluginResult.keepCallback = @YES;
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
    }
}

-(void)didExitContext:(VDARContext *)context {
    if(eventCallbackId) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"exitContext", @"context": context.remoteID ? context.remoteID : @""}];
        
        pluginResult.keepCallback = @YES;
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
    }
}

-(void)errorOccuredOnModelManager:(NSError*)err {
    NSLog(@"Error within PixLive SDK: %@",err);
}

-(void)contextDidRequireSynchronization:(NSArray*)priors {
    [foregroundOperationQueue addOperationWithBlock: ^() {
        dispatch_async(dispatch_get_main_queue(), ^() {
            if(eventCallbackId) {

                NSMutableArray *tags = [NSMutableArray array];

                for(VDARPrior *p in priors) {
                    if([p isKindOfClass:[VDARTagPrior class]]) {
                        VDARTagPrior * tag = (VDARTagPrior*)p;
                        [tags addObject:tag.tagName];
                    }
                }

                CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"requireSync", @"tags": tags}];
                
                pluginResult.keepCallback = @YES;
                
                [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
            }
        });
    }];
}

-(void)annotationViewDidBecomeEmpty {
    if(eventCallbackId) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"hideAnnotations"}];
        
        pluginResult.keepCallback = @YES;
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
    }
}


-(void)annotationViewDidPresentAnnotations {
    if(eventCallbackId) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"type":@"presentAnnotations"}];
        
        pluginResult.keepCallback = @YES;
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
    }
}

@end