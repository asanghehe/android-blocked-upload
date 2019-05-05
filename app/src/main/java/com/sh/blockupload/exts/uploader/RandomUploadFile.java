package com.sh.blockupload.exts.uploader;

import android.util.Log;

import com.sh.blockupload.exts.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RandomUploadFile {

    private String fileLocation;

    private File file;
    private RandomAccessFile random;

    private long currentProgress;

    private String fileName;

    private boolean autoDelete;         //上传完成后，是否删除文件

    private Map<String, String> params = new HashMap<>();

    private int blockNums;

    private final int blockSize = 100 * 1024;

    private int blockIndex = 0;

    private String md5;

    private byte[] cache;

    private int fileSize;

    public RandomUploadFile(String fileLocation){
        this.fileLocation = fileLocation;
    }

    public void destroy(){
        if(this.random != null){
            try {
                this.random.close();

                if(autoDelete) {
                    this.file.delete();
                }

            }catch (IOException e){

            }
        }
        this.random = null;
    }

    public void init() throws FileNotFoundException {

        if(this.random != null){
            return;
        }

        this.file = new File(this.fileLocation);

        this.autoDelete = false;
        this.random = new RandomAccessFile(file, "r");
        this.fileSize = Long.valueOf(file.length()).intValue();
        this.fileName = file.getName();
        this.blockIndex = 0;

        if(file.length() % blockSize == 0){
            blockNums = Long.valueOf(file.length()).intValue() / blockSize;
        }else{
            blockNums = Long.valueOf(file.length() / blockSize).intValue() + 1;
        }
    }

    public String getMd5() {
        if(this.md5 == null) {

            this.md5 = UUID.randomUUID().toString();

            //计算时长不可控
            //this.md5 = this.calcFileMD5();
        }
        return md5;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public int getFileSize() {
        return fileSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? this.params : params;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public String getFileLocation() {
        return fileLocation;
    }

    public void setFileLocation(String fileLocation) {
        this.fileLocation = fileLocation;
    }

    private String calcFileMD5() {

        MessageDigest digest = null;

        byte buffer[] = new byte[1024];
        int len;
        try {

            digest = MessageDigest.getInstance("MD5");
            while ((len = this.random.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }

            //重置指针 到开头
            this.random.seek(0L);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return StringUtils.bytesToHexString(digest.digest());
    }

    public int getBlockNums() {
        return blockNums;
    }

    public int getTransfered(){
        return this.blockIndex;
    }

    public int getPercent(){

        float rate = Integer.valueOf(blockIndex).floatValue() * 100 / Integer.valueOf(this.blockNums).floatValue();

        return Float.valueOf(rate).intValue();
    }

    public byte[] readNextBlock() throws IOException {

        Log.d(getClass().getName(), "currentBlock Index: "+blockIndex +" and blockNums: " + blockNums);

        if(blockIndex > blockNums - 1){
            return null;
        }

        int offset = blockIndex * blockSize;

        byte[] buff = new byte[blockSize];

        random.seek(offset);
        int readed = random.read(buff);
        Log.d(getClass().getName(), "RandomAccessFile offset: "+ offset +"  and readed bytes: " + readed);

        if(readed == -1){
            buff = null;
        }
        if(readed != blockSize){
            buff = Arrays.copyOf(buff, readed);
        }
        this.cache = buff;

        //区块 偏移量 移动
        blockIndex += 1;

        return cache;
    }

    public byte[] currentBlockCache(){
        return this.cache;
    }
}
