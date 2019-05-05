package com.sh.blockupload.exts;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Net {

    private static OkHttpClient client;

    private static Map<String, Call> processors;

    public static final String UPLOAD_URL = "http://10.100.30.12:8080/assist-dock/breakpoint-renewal.do";

    public static void init(){
        processors = new HashMap<>();

        client = new OkHttpClient().newBuilder()
                .connectTimeout(100, TimeUnit.SECONDS)
                .build();
    }


    public static void call(Request req, Callback callback){
        client.newCall(req).enqueue(callback);
    }

    /*****
     * @param url
     * @param fileName
     * @param bytes
     * @throws IOException
     */

    public static void upload(String url, String fileName, byte[] bytes,  Callback callback) throws IOException{

        RequestBody postBody = RequestBody.create(mediaOctedStream, bytes);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, postBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(callback);
    }

    private static final MediaType mediaOctedStream = MediaType.parse("application/octet-stream");

    public static void upload(String url, String fileName, byte[] bytes, final Map<String,String> params,  Callback callback) throws IOException{

        RequestBody postFile = RequestBody.create(mediaOctedStream, bytes);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        StringBuffer urlParams = new StringBuffer();
        String token = null;
        String sec = null;
        if(params != null){
            token = params.get("oauth_token");
            sec = params.get("oauth_token_secret");

            Iterator<String> iter = params.keySet().iterator();
            while(iter.hasNext()){
                String k = iter.next();
                //builder.addFormDataPart(k, params.get(k));
                urlParams.append(k+"="+params.get(k)+"&");
            }
        }
        builder.addFormDataPart("file", fileName, postFile);

        RequestBody requestBody = builder.build();

        String addr = url + "?" + urlParams.toString();
        Log.d(Net.class.getName(), addr);

        Request request = new Request.Builder()
                .header("oauth_token", token)
                .header("oauth_token_secret", sec)
                .url(addr)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(callback);
    }
}
