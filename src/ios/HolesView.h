//
//  HolesView.h
//  test
//
//  Created by Mathieu Monney on 24.04.15.
//
//

#import <UIKit/UIKit.h>

@class PixLive;

@interface HolesView : UIView {
    __weak PixLive *plugin;
}

-(id)initWithFrame:(CGRect)frame andPlugin:(PixLive*)p;

@property (nonatomic) BOOL arTouchEnabled;


@end
