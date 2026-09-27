package cn.album.send;
import android.content.Context;
import android.net.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
final class Discovery {
    static List<String> scan(Context ctx) throws Exception {
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network=cm.getActiveNetwork(); NetworkCapabilities cap=cm.getNetworkCapabilities(network);
        if(cap==null || !(cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) throw new Exception("请连接本地 Wi-Fi 或有线网络");
        LinkProperties links=cm.getLinkProperties(network); List<InetAddress> addresses=new ArrayList<>();
        if(links!=null)for(LinkAddress link:links.getLinkAddresses()) if(link.getAddress() instanceof Inet4Address) {
            byte[] own=link.getAddress().getAddress(); int prefix=link.getPrefixLength();
            // Bound discovery to at most the local /24; larger networks can be entered by host/IP.
            int mask=-1 << (32-Math.max(24,prefix));
            int local=(own[0]&255)<<24|(own[1]&255)<<16|(own[2]&255)<<8|(own[3]&255);
            int subnet=local&mask; long count=1L<<(32-Math.max(24,prefix));
            for(int i=1;i<count-1;i++) { int v=subnet+i; if(v!=local)addresses.add(InetAddress.getByAddress(new byte[]{(byte)(v>>24),(byte)(v>>16),(byte)(v>>8),(byte)v})); }
        }
        ExecutorService pool=Executors.newFixedThreadPool(24); List<Future<String>> futures=new ArrayList<>(); List<String> hosts=new ArrayList<>();
        try {
            for(InetAddress address:addresses) futures.add(pool.submit(()->{
                try(Socket socket=network.getSocketFactory().createSocket()) {socket.connect(new InetSocketAddress(address,445),450);return address.getHostAddress();}catch(Exception e){return null;}
            }));
            for(Future<String> future:futures) {String host=future.get();if(host!=null)hosts.add(host);}
        } finally { pool.shutdownNow(); }
        hosts.sort(Comparator.naturalOrder());return hosts;
    }
}
