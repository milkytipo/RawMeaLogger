package com.example.a2017101705.rawmealogger;

import android.support.annotation.MainThread;

/**
 * Created by 2017101705 on 2018/4/3.
 */

public class GPSConstants {
    private static final long WEEKSEC = 604800;
    private static final long LIGHTSPEED = 299792458;
    private static final int  LEAPSEC=18;//无法预测，根据巴黎国际地球自转服务组织IERS发布闰秒公告得到
    private static final long GPS_L1_FREQ = 1575420000;

    public static long getWeeksec() {
        return WEEKSEC;
    }

    public static long getLightspeed() {
        return LIGHTSPEED;
    }
    public static int getLeapSec() {
        return LEAPSEC;
    }
    public static long getGpsL1Freq(){
        return  GPS_L1_FREQ;
    }
}


