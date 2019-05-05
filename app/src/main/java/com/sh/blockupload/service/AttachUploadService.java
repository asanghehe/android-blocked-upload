package com.sh.blockupload.service;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.sh.blockupload.R;
import com.sh.blockupload.entity.Constants;
import com.sh.blockupload.exts.uploader.Uploader;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class AttachUploadService extends Service {


    public static final String ACTION_START_SERVICE = "io.github.ryanhoo.upload.ACTION.START_UPLOAD";
    public static final String ACTION_STOP_SERVICE = "io.github.ryanhoo.upload.ACTION.STOP_SERVICE";

    public class LocalBinder extends Binder {
        public AttachUploadService getService(){
            return AttachUploadService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private Uploader uploader;

    private Disposable disposable;

    private boolean networkAvailable;

    private class NetworkConnectChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            //检测API是不是小于23，因为到了API23之后getNetworkInfo(int networkType)方法被弃用
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {

                //获得ConnectivityManager对象
                ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                //获取ConnectivityManager对象对应的NetworkInfo对象
                //获取WIFI连接的信息
                NetworkInfo wifiNetworkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                //获取移动数据连接的信息
                NetworkInfo dataNetworkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if (wifiNetworkInfo.isConnected() && dataNetworkInfo.isConnected()) {
                    //Toast.makeText(context, "WIFI已连接,移动数据已连接", Toast.LENGTH_SHORT).show();
                    networkAvailable = true;
                } else if (wifiNetworkInfo.isConnected() && !dataNetworkInfo.isConnected()) {
                    //Toast.makeText(context, "WIFI已连接,移动数据已断开", Toast.LENGTH_SHORT).show();
                    networkAvailable = true;
                } else if (!wifiNetworkInfo.isConnected() && dataNetworkInfo.isConnected()) {
                    //Toast.makeText(context, "WIFI已断开,移动数据已连接", Toast.LENGTH_SHORT).show();
                    networkAvailable = true;
                } else {
                    //Toast.makeText(context, "网络已断开，已暂停上传", Toast.LENGTH_SHORT).show();
                    networkAvailable = false;
                }
                //API大于23时使用下面的方式进行网络监听
            }else {
                //获得ConnectivityManager对象
                ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                //获取所有网络连接的信息
                Network[] networks = connMgr.getAllNetworks();

                boolean connected = false;
                //通过循环将网络信息逐个取出来
                for (int i=0; i < networks.length; i++){
                    //获取ConnectivityManager对象对应的NetworkInfo对象
                    NetworkInfo info = connMgr.getNetworkInfo(networks[i]);
                    if(info.isConnected()){
                        connected = true;
                        break;
                    }
                }
                networkAvailable = connected;
            }
        }
    }

    private NetworkConnectChangedReceiver netWorkStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        uploader = new Uploader();

        networkAvailable = true;

        if (netWorkStateReceiver == null) {
            netWorkStateReceiver = new NetworkConnectChangedReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        registerReceiver(netWorkStateReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(netWorkStateReceiver);

        if(disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        this.uploader.destroy();

        this.netWorkStateReceiver =  null;
        this.disposable = null;
        this.uploader = null;
    }

    public Uploader getUploader(){
        return uploader;
    }

    //手动从界面上选择的文件, 肯定不能上传完了就删除
    public void addAll(List<String> ues, Map<String, String> params){

        if(ues != null && ues.size() > 0) {
            for (String ue : ues) {
                uploader.addTask(ue, params, false);
            }

            showNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //准备支持网络断开暂停 、 恢复上传等操作
        if (intent != null){
            String action = intent.getAction();

            if(ACTION_START_SERVICE.equals(action)) {

                startUpload();

                String visId = intent.getStringExtra("visId");
                String oauth_token = intent.getStringExtra("oauth_token");
                String oauth_token_secret = intent.getStringExtra("oauth_token_secret");
                String file = intent.getStringExtra("file");
                boolean autoDelete = StringUtils.isNotBlank(intent.getStringExtra("autoDelete"));

                String[] files = intent.getStringArrayExtra("files");

                if(visId != null && oauth_token != null && oauth_token_secret != null){
                    Map<String, String> params = new HashMap<>();
                    params.put("visId", visId);
                    params.put("oauth_token", oauth_token);
                    params.put("oauth_token_secret", oauth_token_secret);

                    if(file != null) {
                        uploader.addTask(file, params, autoDelete);
                    }
                    if(files != null){
                        for(String f : files){
                            Log.d(getClass().getName(), f);
                            uploader.addTask(f, params, autoDelete);
                        }
                    }
                }

            }else if(ACTION_STOP_SERVICE.equals(action)){

                pauseUpload();
            }
        }

        return START_STICKY;
    }

    private void pauseUpload(){
        if(disposable != null && !disposable.isDisposed()){
            disposable.dispose();
        }
    }

    public void startUpload(){
        //正在运行
        if(disposable != null && !disposable.isDisposed()){
            return;
        }

        showNotification();

        disposable = Observable.interval(1, 1, TimeUnit.SECONDS)//设置0延迟，每隔一秒发送一条数
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(@NonNull Long value) throws Exception {

                        if(uploader.getProgress() >= 100 || uploader.getFileCount() == 0){
                            uploader.clear();
                            stopForeground(true);
                            disposable.dispose();
                            return;
                        }

                        showNotification();

                        if(networkAvailable==false){
                            return;
                        }

                        if(!uploader.isProcessing()) {
                            uploader.triggerNextBlock();
                        }
                    }
                });
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        //没有任务的时候，内存紧张，停止服务
        if(uploader.getFileCount() == 0){
            stopSelf();
        }
    }

    public void showNotification() {

        Notification notification = new NotificationCompat.Builder(this, Constants.notificationChannel)
                .setSmallIcon(R.drawable.ic_launcher_background)  // the status icon
                .setWhen(System.currentTimeMillis())  // the time stamp
                //.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                .setCustomContentView(getSmallContentView())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false)
                .setSound(null)
                .setVibrate(null)
                .setAutoCancel(true)
                .build();

        startForeground(Constants.notificationUploadId, notification);
    }

    private RemoteViews mContentViewSmall;

    private RemoteViews getSmallContentView() {
        if (mContentViewSmall == null) {
            mContentViewSmall = new RemoteViews(getPackageName(), R.layout.notification_upload_small);
            setUpRemoteView(mContentViewSmall);
        }
        updateRemoteViews(mContentViewSmall);
        return mContentViewSmall;
    }


    private void setUpRemoteView(RemoteViews remoteView) {
        remoteView.setImageViewResource(R.id.notification_progress_layout_iv, R.mipmap.ic_launcher);

        remoteView.setTextViewText(R.id.notification_progress_layout_tv_title, "正在上传");
        remoteView.setTextViewText(R.id.notification_progress_layout_tv_content, "共0文件");
        remoteView.setTextViewText(R.id.notification_progress_layout_tv_content2, "当前进度0%");
        //remoteView.setImageViewResource(R.id.image_view_play_next, R.drawable.ic_remote_view_play_next);
        remoteView.setProgressBar(R.id.notification_progress_layout_pb, 100, 0, false);
    }

    private void updateRemoteViews(RemoteViews remoteView) {

        if(!networkAvailable){
            remoteView.setTextViewText(R.id.notification_progress_layout_tv_title, "暂无网络，已暂停");
        }else{
            remoteView.setTextViewText(R.id.notification_progress_layout_tv_title, "正在上传");
        }
        remoteView.setTextViewText(R.id.notification_progress_layout_tv_content, "共"+ uploader.getFileCount()+"个文件");
        remoteView.setTextViewText(R.id.notification_progress_layout_tv_content2, "当前进度"+uploader.getProgress()+"%");

        remoteView.setProgressBar(R.id.notification_progress_layout_pb, 100, uploader.getProgress(), false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
