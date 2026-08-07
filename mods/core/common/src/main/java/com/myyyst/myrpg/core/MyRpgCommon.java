package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.platform.Services;

public class MyRpgCommon {
    public static void init() {
        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        RpgCondition.bootstrap();
        RpgAction.bootstrap();
    }
}