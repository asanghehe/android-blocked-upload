# android-blocked-upload

安卓文件上传工具，公司内部需要使用文件上传，在github上搜索了一圈没有合适的，因此自己设计开发了此代码，清洗掉公司相关的信息后放到这里


### 实现原理：
------------------------------
使用java 的 RandomFileAccess 文件类， 每次上传时读取一个固定大小的数据块，然后使用Http表单提交，上传到服务器，上传时带上当前区块参数，以及文件总大小参数，
服务器也使用RandomFileAccess 文件类 初始化一个指定大小的文件，在指定位置写入此二进制区块数据，即完成大文件上传任务了

此app依赖了 RxJava 、FastJson、OkHttp 三个外部工具库
		
RxJava 启动一个线程作为定时器，每秒（使用时可以自定义频率）执行一遍请求，请求带上当前文件区块，如果当前Http请求未完成状态，则跳过此次执行，等待下次执行
		
使用这种机制时为了避免上传因为一次失败 断掉，也方便实现暂停上传和恢复上传，缺点就是 速度慢一点（取决于调度频率以及定义的区块大小，每秒一次定时，网络速度比较快的情况下有空闲时间导致）
	
### 使用
------------------------------
Android  manifest.xml中添加service, service中使用Uploader类，操作上传功能，以及管理调度Uploader,
当前上传进度使用Notification在通知栏中显示，APP即使退出前台通知栏一直显示当前进度
Uploader类中有addTask方法 可以在Activity中绑定service后直接调用service的addTask方法添加上传任务，
也可以使用Hbuilder 的h5+ 的html 页面中 使用 startService 方法 传参数 添加上传任务

将Net.java 里面的 UPLOAD_URL 地址换成自己服务端的地址，服务端地址代码参考server目录下的类文件以及说明

``` java
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

    private class NetworkConnectChangedReceiver extends BroadcastReceiver{
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
    public void addAll(List<UploadEntity> ues, Map<String, String> params){

        if(ues != null && ues.size() > 0) {
            for (UploadEntity ue : ues) {
                uploader.addTask(ue.getFilePath(), params, false);
            }

            showNotification();
        }
    }

    public void addTask(String file, Map<String,String> params){
        uploader.addTask(file, params, false);
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //准备支持网络断开暂停 、 恢复上传等操作
        if (intent != null){
            String action = intent.getAction();

            if(ACTION_START_SERVICE.equals(action)) {

                startUpload();

		//用户自行取舍参数， 不用的参数或者报错的参数，自行删除
                String biz = intent.getStringExtra("biz");
                String bizId = intent.getStringExtra("bizId");
                String others = intent.getStringExtra("others");
                String oauth_token = intent.getStringExtra("oauth_token");
                String oauth_token_secret = intent.getStringExtra("oauth_token_secret");
                String file = intent.getStringExtra("file");
                boolean autoDelete = StringUtils.isNotBlank(intent.getStringExtra("autoDelete"));

                String[] files = intent.getStringArrayExtra("files");

                if(bizId != null && oauth_token != null && oauth_token_secret != null){
                    Map<String, String> params = new HashMap<>();
		    //多种附加参数、自定义参数等
                    params.put("biz", biz+"");
                    params.put("bizId", bizId+"");
                    params.put("others", others+"");
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

        return START_NOT_STICKY;
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
	
	//这里引用的Rxjava, 配置gradle中引入
	//    implementation "io.reactivex.rxjava2:rxjava:2.2.8"
    	//    implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
        disposable = Observable.interval(500, 500, TimeUnit.MILLISECONDS)//设置0延迟，每隔一秒发送一块数据
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
	/**
        Notification notification = null; //用户自己初始化 通知对象 并开始显示

        startForeground(Constants.notificationUploadId, notification);
	**/
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
       	//初始化通知栏
    }

    private void updateRemoteViews(RemoteViews remoteView) {
	//通知栏状态变更代码
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
```

Hbuilder HBuilderX 内JS 调用：
``` js
document.getElementById('btn-start-record').addEventListener('tap', function() {
	var main = plus.android.runtimeMainActivity();
	var Intent = plus.android.importClass('android.content.Intent');
	var intent = new Intent();
	intent.setClassName(main, "com.xxx.service.AttachUploadService");	//xxx自行替换包名
	intent.setAction('io.github.ryanhoo.upload.ACTION.START_UPLOAD');
	intent.putExtra('biz', 'visit-attach');
	intent.putExtra('bizId', self.visId + "");
	var files = [];
	files.push("/mnt/filepath/file.name");	//自行添加文件
	intent.putExtra('files', files);
	var name = caseVue.adr && caseVue.adr.adrName ? caseVue.adr.adrName : '未知';
	intent.putExtra('visName', name);
	intent.putExtra('oauth_token', localStorage.getItem('oauthToken'));
	intent.putExtra('oauth_token_secret', localStorage.getItem('oauthTokenSecret'));
	main.startService(intent);
});
```

Java调用 有两种调用方法，一种是在Activity中直接绑定Service,然后调用Service中的方法，一种就是直接在startService的时候传递
### 直接调用使用
``` java
public class UploadQueueActivity extends AppCompatActivity implements View.OnClickListener {

    //private UploadQueueAdapter adapter;

    private Map<String, String> params;

    private AttachUploadService upService;

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {

            AttachUploadService.LocalBinder myBinder = (AttachUploadService.LocalBinder)binder;
            upService = myBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            upService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_upload);

        //adapter = new UploadQueueAdapter();

        //findViewById(R.id.btn_select_file).setOnClickListener(this);
        //findViewById(R.id.btn_do_upload).setOnClickListener(this);

        Bundle exts = getIntent().getExtras();

        params = new HashMap<>();
        if(exts != null) {
            params.put("biz", exts.getString("biz")+"");
            params.put("bizId", exts.getString("bizId")+"");
            params.put("others", exts.getString("others")+"");
            params.put("oauth_token", exts.getString("oauth_token")+"");
            params.put("oauth_token_secret", exts.getString("oauth_token_secret")+"");
        }

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
        //    startForegroundService(new Intent(this, AttachUploadService.class));
        //}else{
            startService(new Intent(this, AttachUploadService.class));
        //}
    }

    @Override
    protected void onStart() {
        super.onStart();
        //绑定Service
        bindService(new Intent(this, AttachUploadService.class), conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //取消绑定Service
        unbindService(conn);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_select_file:
                //this.openSelector2(); 自行定义选择文件的操作
                break;
            case R.id.btn_do_upload:
                upService.addAll(adapter.getData(), this.params);
                //adapter.clear();
                upService.startUpload();
                finish();
                break;
        }
    }
}
```
### 不绑定Service使用
``` java
Intent intent = new Intent(this, AttachUploadService.class);
intent.putExtra("biz", "sssss");
intent.putExtra("bizId", "sssss");

String[] files = {"/filea.jpg", "fileb.jpg"};
intent.putExtra("files", files);
///.....
startService(intent);
```


### 扩展使用
------------------------------
此代码的实现原理即可以用来上传，稍作改造后也可以变成断点下载，改造代码很小

### 优点
------------------------------
- 支持暂停、恢复，网络断开自动暂停自动恢复上传，可靠性不错，逻辑简单，服务端实现简单，附有服务端实现代码
- 支持多文件上传，多文件路径存储在List中，在上传过程中也可以动态继续添加待上传文件，上传完自动从List中移除文件。
- 支持显示总体上传进度，稍作改造亦可以支持每个文件上传进度，以及单个文件的暂停恢复
- 支持 集成到 hbuilder h5+ app 使用
- 支持上传成功后是否自动删除文件，在拍照上传/录音上传时有可能不必保留源文件，录音、拍照添加到任务列表后就可以将autoDelete=true作为参数，上传完直接删除
- 支持附带参数，每个文件添加到任务队列时，可以附带额外业务参数，以提供给服务器做业务处理

### 缺点
------------------------------
在开发此工具初期，是使用Http请求成功后立即执行下一个文件块上传的方式，测试时速度很好，网络利用率满载，用户如果需要的化可以改造成这种用法。不过如果出现网络问题时不会接续上传，暂停恢复需要做一些改造。公司内对上传速度没有要求，所以没有使用此方式实现，而是使用定时器调度上传
因为项目不大，代码不多，没有打包成aar 放到jitpack上，不方便引用，如果后续项目变大变复杂，会考虑放到jitpack上

## 联系我

如有疑问，或者需要帮助支持，请在issue下留言


示例截图：
------------------------------
##### 断开网络情况

![无网络链接情况上传状态图片](/imgs/device-2019-05-05-135652.png)

##### 网络链接情况

![联网情况上传状态图片](/imgs/device-2019-05-05-135710.png)

##### 服务器收到的文件

![服务器收到的文件](/imgs/server-recived-files.png)
