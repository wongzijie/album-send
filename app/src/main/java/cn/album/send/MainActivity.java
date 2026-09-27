package cn.album.send;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements TransferService.Listener {
    private static final int GREEN=0xff176b55, INK=0xff1c302a, MUTED=0xff6b7972, BG=0xfff6f8f5;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<MediaItem> all=new ArrayList<>(), visible=new ArrayList<>();
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private List<ShareProfile> profiles=new ArrayList<>();
    private ProfileStore store;
    private LinearLayout root;
    private TextView count, permissionHint, transferStatus;
    private Button send, retry, cancel;
    private RecyclerView grid;
    private MediaAdapter adapter;
    private Spinner albums;
    private String album="全部相册";
    private int kind=0, loadGeneration=0;
    private boolean preparing=false;
    private boolean profileStoreHealthy=true;
    private boolean dragging=false, paintSelect;
    private int anchor=-1;
    private float dragX,dragY;
    private Set<String> dragBefore;
    private GestureDetector gestures;
    private final Runnable autoScroll=new Runnable(){public void run(){if(!dragging)return;int margin=dp(64);int delta=dragY<margin?-dp(14):dragY>grid.getHeight()-margin?dp(14):0;if(delta!=0){grid.scrollBy(0,delta);paintAt(dragX,Math.max(1,Math.min(grid.getHeight()-1,dragY)));}grid.postDelayed(this,24);}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new ProfileStore(this);
        try{profiles=store.load();}catch(Exception e){profileStoreHealthy=false;error("读取保存的连接失败",e);}
        if(state!=null){ArrayList<String> old=state.getStringArrayList("selected");if(old!=null)selected.addAll(old);}
        build(); if(!hasMedia())requestMedia();
    }
    @Override protected void onResume(){super.onResume();TransferService.listeners.add(this);changed();loadMedia();}
    @Override protected void onPause(){TransferService.listeners.remove(this);super.onPause();}
    @Override protected void onDestroy(){loadGeneration++;grid.removeCallbacks(autoScroll);io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putStringArrayList("selected",new ArrayList<>(selected));}
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(GREEN);b.setAllCaps(false);b.setMinHeight(dp(44));return b;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void build(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(14),0,dp(14),0);setContentView(root);
        if(Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(14)+bars.left,bars.top,dp(14)+bars.right,bars.bottom);return insets;});
        // API 26–29 use the legacy insets accessors.
        if(Build.VERSION.SDK_INT<30) root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(14)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),dp(14)+i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout header=row();TextView title=text("相册快送",25,INK);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(70),1));
        Button targets=button("文件夹");targets.setOnClickListener(v->targets(false));header.addView(targets);
        send=button("发送");send.setTextColor(Color.WHITE);send.setBackground(shape(GREEN,22));send.setOnClickListener(v->targets(true));header.addView(send,new LinearLayout.LayoutParams(dp(82),dp(44)));root.addView(header);
        TextView subtitle=text("你的原片，直接送到家里的共享文件夹。",13,MUTED);root.addView(subtitle);
        LinearLayout filter=row();albums=new Spinner(this);filter.addView(albums,new LinearLayout.LayoutParams(0,dp(54),1));
        String[] kinds={"全部","照片","视频"};Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds));type.setOnItemSelectedListener(new SimpleSelection(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){kind=pos;filter();}});filter.addView(type);root.addView(filter);
        albums.setOnItemSelectedListener(new SimpleSelection(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){album=(String)p.getItemAtPosition(pos);filter();}});
        permissionHint=text("",13,GREEN);permissionHint.setPadding(dp(8),dp(6),dp(8),dp(6));permissionHint.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("相册授权").setItems(new String[]{"重新选择照片 / 授权全部照片和视频","打开系统应用权限设置"},(d,which)->{if(which==0)requestMedia();else startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}).show());root.addView(permissionHint);
        grid=new RecyclerView(this);grid.setLayoutManager(new GridLayoutManager(this,3));grid.setItemAnimator(null);adapter=new MediaAdapter();grid.setAdapter(adapter);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));installDrag();
        LinearLayout footer=row();count=text("未选择 · 长按后滑动可连续勾选",13,MUTED);footer.addView(count,new LinearLayout.LayoutParams(0,dp(54),1));Button selectAll=button("全选");selectAll.setOnClickListener(v->{boolean every=!visible.isEmpty()&&visible.stream().allMatch(m->selected.contains(m.uri.toString()));for(MediaItem m:visible)if(every)selected.remove(m.uri.toString());else selected.add(m.uri.toString());adapter.notifyDataSetChanged();updateSelection();});footer.addView(selectAll);root.addView(footer);
        transferStatus=text("",13,INK);transferStatus.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(transferStatus);
        LinearLayout actions=row();cancel=button("取消发送");cancel.setOnClickListener(v->startService(new Intent(this,TransferService.class).setAction("cancel")));retry=button("重试未完成文件");retry.setOnClickListener(v->{selected.clear();for(MediaItem m:TransferService.failed)selected.add(m.uri.toString());adapter.notifyDataSetChanged();updateSelection();targets(true);});actions.addView(cancel);actions.addView(retry);root.addView(actions);updateSelection();
    }
    abstract static class SimpleSelection implements AdapterView.OnItemSelectedListener {public void onNothingSelected(AdapterView<?> p){}}
    private boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private boolean hasMedia(){return Build.VERSION.SDK_INT>=33?granted(Manifest.permission.READ_MEDIA_IMAGES)||granted(Manifest.permission.READ_MEDIA_VIDEO)||(Build.VERSION.SDK_INT>=34&&granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)):granted(Manifest.permission.READ_EXTERNAL_STORAGE);}
    private void requestMedia(){
        if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},1);
        else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},1);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
    }
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){super.onRequestPermissionsResult(req,perms,results);if(req==1)loadMedia();}
    private void loadMedia(){
        boolean full=Build.VERSION.SDK_INT<33?hasMedia():granted(Manifest.permission.READ_MEDIA_IMAGES)&&granted(Manifest.permission.READ_MEDIA_VIDEO);
        permissionHint.setText(full?"":"当前仅显示已授权内容 · 点这里管理照片和视频授权");permissionHint.setVisibility(full?View.GONE:View.VISIBLE);
        final int generation=++loadGeneration;
        if(!hasMedia()){all.clear();filter();return;}
        io.execute(()->{
            List<MediaItem> result=new ArrayList<>();
            try{
                Uri source=MediaStore.Files.getContentUri("external");
                String[] cols={MediaStore.Files.FileColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.Files.FileColumns.MEDIA_TYPE,MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,MediaStore.Video.VideoColumns.DURATION};
                String where=MediaStore.Files.FileColumns.MEDIA_TYPE+" IN (1,3)";
                try(Cursor c=getContentResolver().query(source,cols,where,null,MediaStore.MediaColumns.DATE_ADDED+" DESC")){
                    if(c!=null)while(c.moveToNext()){
                        boolean video=c.getInt(3)==3;Uri base=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        String name=c.getString(1),bucket=c.getString(4);if(name==null)name="media_"+c.getLong(0);
                        result.add(new MediaItem(ContentUris.withAppendedId(base,c.getLong(0)),name,bucket==null?"其他相册":bucket,c.getLong(2),c.getLong(5),video));
                    }
                }
                runOnUiThread(()->{if(isDestroyed()||generation!=loadGeneration)return;all.clear();all.addAll(result);Set<String> accessible=new HashSet<>();for(MediaItem m:all)accessible.add(m.uri.toString());selected.retainAll(accessible);
                    TreeSet<String> buckets=new TreeSet<>();for(MediaItem m:all)buckets.add(m.album);List<String> options=new ArrayList<>();options.add("全部相册");options.addAll(buckets);
                    String old=album;albums.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));int index=options.indexOf(old);albums.setSelection(Math.max(0,index));album=options.get(Math.max(0,index));filter();});
            }catch(Exception e){runOnUiThread(()->{if(generation==loadGeneration)error("无法读取相册",e);});}
        });
    }
    private void filter(){if(adapter==null)return;dragging=false;visible.clear();for(MediaItem m:all)if((album.equals("全部相册")||album.equals(m.album))&&(kind==0||kind==1&&!m.video||kind==2&&m.video))visible.add(m);adapter.notifyDataSetChanged();updateSelection();}
    private void updateSelection(){if(count==null)return;long size=0;for(MediaItem m:all)if(selected.contains(m.uri.toString()))size+=m.size;count.setText(selected.isEmpty()?(visible.isEmpty()?"没有可见的照片或视频":"长按后滑动可连续勾选"):"已选 "+selected.size()+" 项 · "+android.text.format.Formatter.formatShortFileSize(this,size));send.setText(selected.isEmpty()?"发送":"发送 "+selected.size());send.setEnabled(!selected.isEmpty()&&!TransferService.running&&!preparing);}
    @Override public void changed(){if(TransferService.running)preparing=false;if(transferStatus==null)return;transferStatus.setText(TransferService.status);transferStatus.setVisibility(TransferService.status.isEmpty()?View.GONE:View.VISIBLE);cancel.setVisibility(TransferService.running?View.VISIBLE:View.GONE);retry.setVisibility(!TransferService.running&&!TransferService.failed.isEmpty()?View.VISIBLE:View.GONE);updateSelection();}
    private void toggle(int pos){if(pos<0||pos>=visible.size())return;String id=visible.get(pos).uri.toString();if(!selected.add(id))selected.remove(id);adapter.notifyItemChanged(pos);updateSelection();}
    private int at(float x,float y){View child=grid.findChildViewUnder(x,y);return child==null?-1:grid.getChildAdapterPosition(child);}
    private void paintAt(float x,float y){int pos=at(x,y);if(pos<0||anchor<0)return;selected.clear();selected.addAll(dragBefore);for(int i=Math.min(anchor,pos);i<=Math.max(anchor,pos);i++){String id=visible.get(i).uri.toString();if(paintSelect)selected.add(id);else selected.remove(id);}adapter.notifyDataSetChanged();updateSelection();}
    private void installDrag(){
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){@Override public void onLongPress(android.view.MotionEvent e){anchor=at(e.getX(),e.getY());if(anchor<0)return;dragBefore=new HashSet<>(selected);paintSelect=!selected.contains(visible.get(anchor).uri.toString());dragX=e.getX();dragY=e.getY();dragging=true;grid.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);grid.getParent().requestDisallowInterceptTouchEvent(true);paintAt(dragX,dragY);grid.post(autoScroll);}});
        grid.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener(){@Override public boolean onInterceptTouchEvent(RecyclerView r,MotionEvent e){gestures.onTouchEvent(e);return dragging;}@Override public void onTouchEvent(RecyclerView r,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_MOVE){dragX=e.getX();dragY=e.getY();paintAt(dragX,dragY);}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;grid.removeCallbacks(autoScroll);grid.getParent().requestDisallowInterceptTouchEvent(false);}}});
    }
    class MediaAdapter extends RecyclerView.Adapter<MediaHolder>{
        @Override public MediaHolder onCreateViewHolder(android.view.ViewGroup parent,int type){FrameLayout frame=new FrameLayout(MainActivity.this);frame.setPadding(dp(2),dp(2),dp(2),dp(2));int size=(getResources().getDisplayMetrics().widthPixels-dp(28))/3;frame.setLayoutParams(new RecyclerView.LayoutParams(-1,size));ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));TextView mark=text("○",22,Color.WHITE);mark.setGravity(Gravity.CENTER);mark.setBackground(shape(0x88000000,20));FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END);mp.setMargins(0,dp(6),dp(6),0);frame.addView(mark,mp);TextView video=text("",11,Color.WHITE);video.setPadding(dp(6),0,dp(6),0);video.setBackgroundColor(0x99000000);frame.addView(video,new FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM));return new MediaHolder(frame,image,mark,video);}
        @Override public void onBindViewHolder(MediaHolder h,int pos){MediaItem m=visible.get(pos);Glide.with(MainActivity.this).load(m.uri).centerCrop().into(h.image);boolean picked=selected.contains(m.uri.toString());h.mark.setText(picked?"✓":"○");h.mark.setBackground(shape(picked?GREEN:0x88000000,20));h.itemView.setAlpha(picked?.78f:1f);h.video.setVisibility(m.video?View.VISIBLE:View.GONE);h.video.setText(String.format(Locale.ROOT,"▶ %d:%02d",m.duration/60000,m.duration/1000%60));h.itemView.setContentDescription(m.name+(picked?"，已选择":"，未选择"));h.itemView.setOnClickListener(v->toggle(h.getBindingAdapterPosition()));}
        @Override public int getItemCount(){return visible.size();}
    }
    static class MediaHolder extends RecyclerView.ViewHolder{ImageView image;TextView mark,video;MediaHolder(View v,ImageView i,TextView m,TextView d){super(v);image=i;mark=m;video=d;}}

    private void error(String title,Exception e){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(TransferService.friendly(e)).setPositiveButton("知道了",null).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void saveProfiles() throws Exception{if(!profileStoreHealthy)throw new Exception("保存的连接无法解密，暂时不能修改；请清除应用数据后重新添加连接");store.save(profiles);}
    private void targets(boolean sending){
        if(sending&&(selected.isEmpty()||TransferService.running||preparing))return;
        LinearLayout body=column();body.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView note=text(sending?"选择一个目标，再进入需要的子目录。":"连接只保存在这台手机上。长按连接可编辑或删除。",13,MUTED);body.addView(note);
        LinearLayout options=row();Button add=button("＋ 添加共享");Button discover=button("搜索局域网");options.addView(add);options.addView(discover);body.addView(options);
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(300)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(sending?"发送到共享文件夹":"我的共享文件夹").setView(body).setNegativeButton("关闭",null).create();
        if(profiles.isEmpty()){TextView empty=text("还没有保存的连接\n添加 NAS / 电脑的地址，或搜索附近设备。",15,MUTED);list.addView(empty);}
        for(ShareProfile p:profiles){Button b=button((p.label.isEmpty()?p.host:p.label)+"\n"+p.host+" / "+(p.share.isEmpty()?"选择共享":p.share)+displayPath(p.path));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);list.addView(b,new LinearLayout.LayoutParams(-1,dp(70)));b.setOnClickListener(v->{dialog.dismiss();browse(p,p.url(),sending);});b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(p.label.isEmpty()?p.host:p.label).setItems(new String[]{"编辑连接","删除连接"},(d,which)->{if(which==0){dialog.dismiss();editProfile(p,p.host,sending);}else{List<ShareProfile> old=new ArrayList<>(profiles);profiles.remove(p);try{saveProfiles();dialog.dismiss();targets(sending);}catch(Exception e){profiles=old;error("无法删除",e);}}}).show();return true;});}
        add.setOnClickListener(v->{dialog.dismiss();editProfile(null,"",sending);});
        discover.setOnClickListener(v->{discover.setEnabled(false);discover.setText("正在搜索…");io.execute(()->{try{List<String> hosts=Discovery.scan(this);runOnUiThread(()->{if(!dialog.isShowing())return;discover.setEnabled(true);discover.setText("搜索局域网");if(hosts.isEmpty()){toast("未发现设备，可手动输入 IP 或主机名");return;}new AlertDialog.Builder(this).setTitle("发现 SMB 设备 · 端口 445").setItems(hosts.toArray(new String[0]),(d,pos)->{dialog.dismiss();editProfile(null,hosts.get(pos),sending);}).setNegativeButton("关闭",null).show();});}catch(Exception e){runOnUiThread(()->{if(dialog.isShowing()){discover.setEnabled(true);discover.setText("搜索局域网");error("搜索失败",e);}});}});});dialog.show();
    }
    private String displayPath(String path){return path;}
    private EditText field(LinearLayout body,String hint,String value,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setTextSize(15);e.setInputType(password?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);body.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));return e;}
    private void editProfile(ShareProfile existing,String host,boolean sending){
        LinearLayout body=column();body.setPadding(dp(20),0,dp(20),0);ScrollView scroll=new ScrollView(this);scroll.addView(body);
        EditText label=field(body,"连接名称，例如：家里的 NAS",existing==null?"":existing.label,false);
        EditText address=field(body,"IP / 主机名，例如 192.168.1.10",host,false);
        EditText share=field(body,"共享名称（可留空，连接后选择）",existing==null?"":existing.share,false);
        EditText user=field(body,"用户名（访客访问可留空）",existing==null?"":existing.user,false);
        EditText pass=field(body,"密码",existing==null?"":existing.password,true);
        EditText domain=field(body,"域 / 工作组（选填）",existing==null?"":existing.domain,false);
        TextView hint=text("填写共享名称，不包含子目录。连接后可逐级选择目录。",12,MUTED);body.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"添加共享连接":"编辑共享连接").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存并浏览",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String h=address.getText().toString().trim();String s=share.getText().toString().trim();
            if(!h.matches("[A-Za-z0-9_.-]+")||h.equals(".")||h.equals("..")){address.setError("请输入 IPv4 地址或主机名，不包含 smb:// 或目录");return;}
            if(s.contains("/")||s.contains("\\")||s.equals(".")||s.equals("..")){share.setError("只填共享名称，子目录在下一页选择");return;}
            ShareProfile p=new ShareProfile();p.label=label.getText().toString().trim();p.host=h;p.share=s;p.user=user.getText().toString().trim();p.password=pass.getText().toString();p.domain=domain.getText().toString().trim();
            if(existing!=null){p.id=existing.id;if(p.host.equals(existing.host)&&p.share.equals(existing.share))p.path=existing.path;}
            List<ShareProfile> old=new ArrayList<>(profiles);if(existing!=null)profiles.remove(existing);profiles.add(p);
            try{saveProfiles();dialog.dismiss();browse(p,p.url(),sending);}catch(Exception e){profiles=old;error("无法保存连接",e);}
        }));dialog.show();
    }
    private void browse(ShareProfile p,String initial,boolean sending){
        LinearLayout body=column();body.setPadding(dp(18),0,dp(18),dp(8));TextView path=text("",14,INK);path.setMaxLines(3);body.addView(path);
        LinearLayout actions=row();Button up=button("↑ 上一级"),refresh=button("刷新");actions.addView(up);actions.addView(refresh);body.addView(actions);
        ScrollView sc=new ScrollView(this);LinearLayout folders=column();sc.addView(folders);body.addView(sc,new LinearLayout.LayoutParams(-1,dp(300)));
        TextView state=text("",13,MUTED);body.addView(state);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择目标文件夹").setView(body).setNegativeButton("关闭",null).setPositiveButton(sending?"发送到这里":"保存此文件夹",null).create();
        final String[] current={initial};final int[] version={0};
        class Browser {
            void load(String url){current[0]=url;int request=++version[0];path.setText(displayPath(url.replace("smb://","")));folders.removeAllViews();state.setText("正在读取目录…");dialog.getButton(-1).setEnabled(false);up.setEnabled(!url.equals("smb://"+p.host+"/"));
                io.execute(()->{try(SmbClient client=new SmbClient(p)){List<SmbClient.Folder> entries=client.folders(url);runOnUiThread(()->{if(!dialog.isShowing()||request!=version[0])return;state.setText(entries.isEmpty()?"此目录没有子文件夹":"共 "+entries.size()+" 个子文件夹");boolean insideShare=!url.equals("smb://"+p.host+"/");dialog.getButton(-1).setEnabled(insideShare);for(SmbClient.Folder folder:entries){Button b=button("▸  "+folder.name());b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);folders.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->load(folder.url()));}});}catch(Exception e){runOnUiThread(()->{if(dialog.isShowing()&&request==version[0])state.setText("连接失败："+TransferService.friendly(e)+"\n可返回编辑账号、共享名称或地址。");});}});
            }
        }
        Browser browser=new Browser();dialog.setOnShowListener(d->{up.setOnClickListener(v->{String trimmed=current[0].substring(0,current[0].length()-1);int cut=trimmed.lastIndexOf('/');if(cut>5)browser.load(trimmed.substring(0,cut+1));});refresh.setOnClickListener(v->browser.load(current[0]));dialog.getButton(-1).setOnClickListener(v->{
            try{
                String relative=current[0].substring(("smb://"+p.host+"/").length());int slash=relative.indexOf('/');if(slash<1)throw new Exception("请先选择共享文件夹");
                String previousShare=p.share,previousPath=p.path;p.share=displayPath(relative.substring(0,slash));p.path=relative.substring(slash+1);
                try{saveProfiles();}catch(Exception e){p.share=previousShare;p.path=previousPath;throw e;}
                if(sending){dialog.dismiss();enqueue(p,current[0]);}else{dialog.dismiss();toast("已保存，下次可直接打开这个目录");}
            }catch(Exception e){error("无法选择目标",e);}
        });browser.load(initial);});dialog.show();
    }
    private void enqueue(ShareProfile profile,String url){
        if(TransferService.running||preparing)return;
        List<MediaItem> items=new ArrayList<>();for(MediaItem m:all)if(selected.contains(m.uri.toString()))items.add(m);
        if(items.isEmpty()){toast("请先选择照片或视频");return;}
        preparing=true;updateSelection();
        try{
            JSONArray files=new JSONArray();for(MediaItem m:items)files.put(m.json());String filename="job-"+UUID.randomUUID()+".json";
            java.nio.file.Files.write(new File(getFilesDir(),filename).toPath(),new JSONObject().put("profile",profile.id).put("url",url).put("items",files).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(Build.VERSION.SDK_INT>=33&&!granted(Manifest.permission.POST_NOTIFICATIONS))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);
            startForegroundService(new Intent(this,TransferService.class).putExtra("job",filename));
            selected.clear();adapter.notifyDataSetChanged();toast("已开始发送，可在通知栏查看进度");
        }catch(Exception e){preparing=false;error("无法开始发送",e);}finally{updateSelection();}
    }
}
