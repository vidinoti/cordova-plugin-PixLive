/********* PixLive.m Cordova Plugin Implementation *******/

#import "PixLive.h"
#import <Cordova/CDV.h>
#import <VDARSDK/VDARSDK.h>
#import "MyCameraImageSource.h"
#import "IonicARViewController.h"



@implementation PixLive


-(CDVPlugin*) initWithWebView:(UIWebView*)theWebView
{
    self = (PixLive*)[super initWithWebView:theWebView];
    
    arViewControllers = [NSMutableDictionary dictionary];
    
    return self;
}

-(void)beforeLeave:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl viewWillDisappear:NO];
}

-(void)afterLeave:(CDVInvokedUrlCommand *)command {
    NSArray* arguments = [command arguments];
    
    NSUInteger argc = [arguments count];
    
    if (argc < 1) {
        return;
    }
    
    NSUInteger ctrlID = [[arguments objectAtIndex:0] unsignedIntegerValue];
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
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
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    ctrl.view.frame = ctrl.sizeARView;
    
    [ [ [ self viewController ] view ] addSubview:ctrl.view];
    
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
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
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
    [VDARSDKController sharedInstance].enableNotifications=YES;
    
    MyCameraImageSource *cameraSource=[[MyCameraImageSource alloc] init];
    
    [VDARSDKController sharedInstance].imageSender=cameraSource;
    
    [[VDARSDKController sharedInstance].afterLoadingQueue addOperationWithBlock:^{
        dispatch_async(dispatch_get_main_queue(), ^{
            [[VDARRemoteController sharedInstance] syncRemoteModelsAsynchronouslyWithPriors:@[[VDARTagPrior tagWithName:@"release_test"]] withCompletionBlock:^(id result, NSError *err) {
                NSLog(@"Synced.");
            }];
        });
    }];
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
    
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    ctrl.sizeARView = viewRect;
    
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
    IonicARViewController * ctrl = [arViewControllers objectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
    
    [ctrl.view removeFromSuperview];
    
    [arViewControllers removeObjectForKey:[NSNumber numberWithUnsignedInteger:ctrlID]];
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
    
    IonicARViewController * ctrl = arViewControllers[[NSNumber numberWithUnsignedInteger:ctrlID]] = [[IonicARViewController alloc] initWithPlugin:self];
    
    ctrl.sizeARView = viewRect;
    
    [ctrl view]; //Load the view
    //Manually triggers the events
    [ctrl viewDidLoad];
    
    ctrl.view.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
    
    [ [ [ self viewController ] view ] addSubview:ctrl.view];
    
    ctrl.view.frame = viewRect;
    
    [ctrl.view setNeedsLayout];
    
    [ctrl viewWillAppear:NO];
    
    [ctrl viewDidAppear:NO];
    
    
}


@end