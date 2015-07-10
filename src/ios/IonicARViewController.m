//
//  IonicARViewController.m
//  myApp
//
//  Created by Mathieu Monney on 15.04.15.
//
//

#import "IonicARViewController.h"
#import "PixLive.h"

@interface IonicARViewController () {
    __weak PixLive* plugin;
}

@end

@implementation IonicARViewController

-(id)initWithPlugin:(PixLive*)_plugin {
    self = [super init];

    plugin = _plugin;

    self.annotationView.darkScreen = false;
    
    return self;
}

-(void)presentModalViewController:(UIViewController *)modalViewController animated:(BOOL)animated {
    [self presentViewController:modalViewController animated:animated completion:nil];
}

-(void)presentViewController:(UIViewController *)viewControllerToPresent animated:(BOOL)flag completion:(void (^)(void))completion {
    // Artificially generate those events as this controller is not in the event hierarchy
    [self viewWillDisappear:NO];
    [self viewDidDisappear:NO];
    
    [plugin presentViewController:viewControllerToPresent animated:flag completion:completion];
}

-(void)annotationViewDidBecomeEmpty {
    [super annotationViewDidBecomeEmpty];

    [plugin annotationViewDidBecomeEmpty];
}


-(void)annotationViewDidPresentAnnotations {
    [super annotationViewDidPresentAnnotations];

    [plugin annotationViewDidPresentAnnotations];
}


@end
