package com.sh.blockupload;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;

import com.sh.blockupload.entity.Constants;

public class UploadBaseApplication extends Application {


    /**
     * 新版本安卓通知Channel 初始化
     */
    private void initNotificationChannel(){
        //创建通知通道
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel1 = new NotificationChannel(Constants.notificationChannel, "通知渠道1", NotificationManager.IMPORTANCE_LOW);
            channel1.setDescription("通知渠道的描述1");
            channel1.enableLights(true);
            channel1.setLightColor(Color.WHITE);
            //createNotificationChannel(channel1);

            NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel1);
        }
    }
}
