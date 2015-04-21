//
//  PixLive.h
//  VDARSDK Ionic
//
//  Created by Mathieu Monney on 15.04.15.
//
//  Copyright 2010-2015 Vidinoti SA. All rights reserved.
//


#import <VDARSDK/VDARSDK.h>
#import <Cordova/CDV.h>

@interface PixLive : CDVPlugin {
    NSMutableDictionary *arViewControllers;
}

@property (nonatomic) CGRect sizeARView;

-(void)presentViewController:(UIViewController *)viewControllerToPresent animated:(BOOL)flag completion:(void (^)(void))completion;

@end
