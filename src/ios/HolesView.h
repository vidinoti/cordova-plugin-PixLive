//
//  HolesView.h
//  test
//
//  Created by Mathieu Monney on 24.04.15.
//
//  Copyright 2010-2016 Vidinoti SA. All rights reserved.
//

#import <UIKit/UIKit.h>

@class PixLive;

@interface HolesView : UIView {
    __weak PixLive *plugin;
}

-(id)initWithFrame:(CGRect)frame andPlugin:(PixLive*)p;

/**
 * Defines the "touch hole" region (i.e. the region where touch events are not
 * intercepted by the plugin
 * @param top the top coordinate of the region
 * @param bottom the bottom coordinate of the region
 * @param left the left coordinate of the region
 * @param right the right coordinate of the region
 */
-(void)setTouchHoleWithTop: (int)top bottom: (int)bottom left: (int)left right: (int)right;

@property (nonatomic) BOOL arTouchEnabled;
@property (nonatomic) CGRect touchHole;


@end
