//
//  CordovaARViewController.m
//  myApp
//
//  Created by Mathieu Monney on 15.04.15.
//
//  Copyright 2010-2016 Vidinoti SA. All rights reserved.
//

#import "CordovaARViewController.h"
#import "PixLive.h"

@interface CordovaARViewController () {
    __weak PixLive* plugin;
}

@end

@implementation CordovaARViewController

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
    
    switch(viewControllerToPresent.modalPresentationStyle) {
        case UIModalPresentationFullScreen:
        case UIModalPresentationCustom:
        case UIModalPresentationOverFullScreen:
        case UIModalPresentationNone:
            // Artificially generate those events as this controller is not in the event hierarchy
            [self viewWillDisappear:NO];
            [self viewDidDisappear:NO];
            break;
            
        case UIModalPresentationPageSheet:
        case UIModalPresentationFormSheet:
            if(UI_USER_INTERFACE_IDIOM()!=UIUserInterfaceIdiomPad) {
                [self viewWillDisappear:NO];
                [self viewDidDisappear:NO];
            }
            
        default:
        case UIModalPresentationPopover:
        case UIModalPresentationCurrentContext:
            case UIModalPresentationOverCurrentContext:
            break;
    }
    
    
   
    
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

/**
 * Creates a capture of the AR view and saves it in the camera roll.
 * Returns YES if success, NO otherwise.
 */
-(BOOL)captureScreenshot {
    UIImage* screenshot = [self.annotationView captureScreenshot];
    if (screenshot) {
        UIImageWriteToSavedPhotosAlbum(screenshot, nil, nil, nil);
        return YES;
    }
    return NO;
}

@end
