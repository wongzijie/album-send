package cn.album.send;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import jcifs.smb.SmbFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
public class TransferService extends Service {
    static final String CHANNEL="transfers";
    static volatile boolean running=false;
    static volatile String status="";
    static volatile int progress=0;
    static volatile List<MediaItem> failed=Collections.emptyList();
    interface Listener { void changed(); }
    static final Set<Listener> listeners=new CopyOnWriteArraySet<>();
    private volatile boolean cancel=false;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wake;
    private long lastUpdate;
    private static void changed() {new Handler(Looper.getMainLooper()).post(()->{for(Listener l:listeners)l.changed();});}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"文件发送",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())) {cancel=true;if(!running)stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        running=true;cancel=false;progress=0;failed=Collections.emptyList();status="正在连接共享文件夹…";
        startForeground(7,notification(status,true)); changed();
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AlbumSend:upload"); wake.acquire(6*60*60*1000L);
        String job=intent.getStringExtra("job"); executor.execute(()->transfer(job));
        return START_NOT_STICKY;
    }
    private Notification notification(String text,boolean active) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(cn.album.send.R.drawable.ic_app).setContentTitle("相册快送").setContentText(text).setContentIntent(open).setOngoing(active).setOnlyAlertOnce(true);
        if(active){b.setProgress(100,progress,false);b.addAction(new Notification.Action.Builder(null,"取消",PendingIntent.getService(this,1,new Intent(this,TransferService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE)).build());}
        return b.build();
    }
    private void update(String text,int pct,boolean force) {
        if(!force&&System.currentTimeMillis()-lastUpdate<300)return;
        lastUpdate=System.currentTimeMillis();status=text;progress=pct;
        getSystemService(NotificationManager.class).notify(7,notification(text,true));changed();
    }
    private void check() throws IOException {if(cancel||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消");}
    private void transfer(String job) {
        List<MediaItem> items=new ArrayList<>(),bad=new ArrayList<>(); JSONArray receipts=new JSONArray(); int success=0; String finalText;
        try {
            JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(),job).toPath()),StandardCharsets.UTF_8));
            JSONArray media=j.getJSONArray("items");for(int i=0;i<media.length();i++)items.add(MediaItem.from(media.getJSONObject(i)));
            ShareProfile target=null;for(ShareProfile p:new ProfileStore(this).load())if(p.id.equals(j.getString("profile")))target=p;
            if(target==null)throw new IOException("连接已被删除，请重新选择");
            String url=j.getString("url");if(!url.startsWith(target.root())||url.equals("smb://"+target.host+"/"))throw new IOException("请选择共享中的目标目录");
            long total=0,done=0;for(MediaItem m:items)total+=m.size;
            try(SmbClient client=new SmbClient(target)) {
                for(int index=0;index<items.size();index++) {
                    MediaItem m=items.get(index); if(cancel){bad.addAll(items.subList(index,items.size()));break;}
                    String outName=""; long current=0;
                    try {
                        String name=Names.safe(m.name); String tempName=".album-send-"+UUID.randomUUID()+".part";
                        try(SmbFile temp=client.file(url+tempName)) {
                            temp.createNewFile(); boolean committed=false;
                            try {
                                try(InputStream in=getContentResolver().openInputStream(m.uri);OutputStream out=temp.getOutputStream()) {
                                    if(in==null)throw new IOException("无法读取原文件");byte[] buffer=new byte[256*1024];int n;
                                    while((n=in.read(buffer))!=-1){check();out.write(buffer,0,n);current+=n;int pct=total>0?(int)Math.min(99,100.0*(done+current)/total):index*100/items.size();update("正在发送 "+(index+1)+"/"+items.size()+" · "+m.name,pct,false);}
                                }
                                check(); if(m.size>0&&current!=m.size)throw new IOException("原文件大小发生变化，请重新选择");
                                if(temp.length()!=current)throw new IOException("远端文件大小校验失败");
                                for(int collision=0;collision<10000;collision++) {
                                    outName=Names.numbered(name,collision);
                                    try(SmbFile destination=client.file(url+outName)) {
                                        if(destination.exists())continue;
                                        try{temp.renameTo(destination,false);committed=true;break;}catch(Exception e){if(destination.exists())continue;throw e;}
                                    }
                                }
                                if(!committed)throw new IOException("同名文件太多，无法生成新名称");
                            } finally {if(!committed)try{temp.delete();}catch(Exception ignored){}}
                        }
                        success++;receipts.put(new JSONObject().put("name",m.name).put("destination",outName).put("bytes",current).put("ok",true));
                    } catch(Exception e) {bad.add(m);receipts.put(new JSONObject().put("name",m.name).put("ok",false).put("error",cancel?"已取消":friendly(e)));}
                    done+=m.size;
                }
            }
            finalText=(cancel?"已取消 · ":"发送完成 · ")+success+" 个成功"+(bad.isEmpty()?"":"，"+bad.size()+" 个未完成");
        } catch(Exception e) {bad=new ArrayList<>(items);finalText="发送失败："+friendly(e);}
        failed=Collections.unmodifiableList(new ArrayList<>(bad));status=finalText;progress=success==items.size()&&!items.isEmpty()?100:progress;running=false;
        try {java.nio.file.Files.write(new File(getFilesDir(),"last-transfer.json").toPath(),new JSONObject().put("summary",finalText).put("files",receipts).toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        if(job!=null)new File(getFilesDir(),job).delete();
        if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).notify(8,notification(finalText,false));changed();stopSelf();
    }
    static String friendly(Exception e) {String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    @Override public void onTimeout(int startId,int fgsType) {cancel=true;executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();running=false;status="发送超时，未完成文件请重新选择发送";changed();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy() {cancel=true;executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
