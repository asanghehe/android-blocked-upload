package com.sh.blockupload.exts.uploader;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sh.blockupload.exts.Net;

import org.apache.commons.lang3.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class Uploader {


    private List<RandomUploadFile> list;
    private int currentIndex = -1;

    private List<UploadCallback> callbacks;

    public Uploader(){
        this.list = new ArrayList<>();
        this.currentIndex = -1;

        this.callbacks = new ArrayList<>();
    }

    /**
     * 注册回调
     * @param callback
     */
    public void regist(UploadCallback callback){
        this.callbacks.add(callback);
    }

    /**
     * 取消注册回调
     * @param callback
     */
    public void unregist(UploadCallback callback){
        this.callbacks.remove(callback);
    }

    public void destroy(){
        if(this.list != null){
            for(RandomUploadFile t : this.list){
                t.destroy();
            }
            this.list.clear();
        }
        if(this.callbacks != null){
            this.callbacks.clear();
        }

        this.list = null;
        this.callbacks = null;
    }
    /**
     * 添加任务
     */
    public synchronized void addTask(String file, Map<String,String> params, boolean autoDelete){
        boolean hasContain = false;
        for(RandomUploadFile t : this.list){
            if(StringUtils.equals(file, t.getFileLocation())){
                hasContain = true;
                break;
            }
        }
        if(hasContain){
            return;
        }

        RandomUploadFile f = new RandomUploadFile(file);
        if(params != null){
            Iterator<String> iter = params.keySet().iterator();
            while(iter.hasNext()){
                String k = iter.next();
                f.getParams().put(k, params.get(k));
            }
        }

        try{
            f.init();
        }catch (FileNotFoundException e){

        }
        f.setAutoDelete(autoDelete);

        this.list.add(f);
    }

    public synchronized void addTask(String file, Map<String,String> params){
        addTask(file, params, false);
    }

    public int getFileCount(){
        return this.list.size();
    }

    public int getProgress(){
        if(this.getFileCount() == 0){
            return 0;
        }
        int blockTotal = 0;
        int blockPassed = 0;
        for(RandomUploadFile f : this.list){
            blockTotal += f.getBlockNums();
            blockPassed += f.getTransfered();
        }
        if(blockTotal == 0){
            return 0;
        }
        float progress = Integer.valueOf(blockPassed).floatValue() / Integer.valueOf(blockTotal).floatValue();
        return Float.valueOf(progress * 100).intValue();
    }

    private RandomUploadFile getCurrent(){
        if(this.list.size()==0 || this.currentIndex < 0){
            return null;
        }
        if(this.currentIndex + 1 > this.list.size()){
            return null;
        }
        RandomUploadFile file = this.list.get(this.currentIndex);
        try {
            file.init();
        }catch (Exception e){

        }

        return file;
    }

    private RandomUploadFile getNext(){
        this.currentIndex ++;
        return getCurrent();
    }

    public void clear(){
        this.list.clear();
        this.currentIndex = -1;
    }

    private boolean httpProcessing = false;
    private boolean lastPackageFailure = false;

    public boolean isProcessing(){
        return httpProcessing;
    }

    /**
     * 外部定时调用，方便暂停恢复等
     */
    public void triggerNextBlock(){

        byte[] buffer = null;

        RandomUploadFile file = this.getCurrent();

        //起步，第一个文件
        if (file == null) {
            file = this.getNext();
        }
        if (file == null) {
            return;
        }

        //失败重试机制
        if(lastPackageFailure){
            buffer = file.currentBlockCache();
        }else {

            try {
                buffer = file.readNextBlock();
            } catch (IOException e) {

            }

            if (buffer == null) {

                //下一个文件
                file = this.getNext();
                if (file == null) {
                    return;
                }

                try {
                    buffer = file.readNextBlock();
                } catch (IOException e) {

                }
            }
        }
        if(buffer == null){
            return;
        }

        Map<String,String> params = file.getParams();
        params.put("filemd5", file.getMd5()+"");
        params.put("filesize", file.getFileSize()+"");
        params.put("blockSize", file.getBlockSize()+"");
        params.put("blockIndex", file.getTransfered()+"");      //提供给服务器 判断进度

        Log.d(getClass().getName(), "execute Upload");

        try {
            httpProcessing = true;
            lastPackageFailure = false;

            Net.upload(Net.UPLOAD_URL, file.getFileName(), buffer, params, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    //Toast.makeText(getApplicationContext(), "fail", Toast.LENGTH_LONG).show();
                    httpProcessing = false;
                    lastPackageFailure = true;
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    final String responseStr = response.body().string();

                    if(200 <= response.code() && response.code() <= 299){
                        JSONObject json = JSONObject.parseObject(responseStr);
                        if(json.getBoolean("success")){
                            lastPackageFailure = false;
                        }else{
                            lastPackageFailure = true;
                        }
                    }else{
                        lastPackageFailure = true;
                    }

                    Log.d(getClass().getName(), responseStr);

                    httpProcessing = false;
                }
            });
        }catch (IOException e){

        }
    }
}
