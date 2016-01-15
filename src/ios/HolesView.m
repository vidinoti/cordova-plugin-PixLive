//
//  HolesView.m
//  test
//
//  Created by Mathieu Monney on 24.04.15.
//
//  Copyright 2010-2016 Vidinoti SA. All rights reserved.
//

#import "HolesView.h"
#import "PixLive.h"
#import "CordovaARViewController.h"

@implementation HolesView


-(id)initWithFrame:(CGRect)frame andPlugin:(PixLive*)p {
    self = [super initWithFrame:frame];
    
    plugin = p;
    
    self.arTouchEnabled = NO;
    
    self.multipleTouchEnabled = YES;
    
    return self;
}

- (BOOL)pointInside:(CGPoint)point withEvent:(UIEvent *)event {
    
    if(!self.arTouchEnabled || plugin.arViewControllers.count==0) {
        return YES;
    }
    
    //Check if we fall into one AR view
    
    for(CordovaARViewController *ctrl in plugin.arViewControllers.allValues) {
        if(!ctrl.view.superview || ctrl.view.hidden) {
            continue;
        }
        
        CGPoint arViewLocation = [ctrl.view convertPoint:point fromView:self];
        
        if(arViewLocation.x>=0 && arViewLocation.y>=0 && arViewLocation.x<ctrl.view.frame.size.width && arViewLocation.y<ctrl.view.frame.size.height) {
            return NO;
        }
    }
    
    return YES;
}


@end
