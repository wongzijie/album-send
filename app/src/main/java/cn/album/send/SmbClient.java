package cn.album.send;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
final class SmbClient implements AutoCloseable {
    private final CIFSContext base, authenticated;
    SmbClient(ShareProfile p) throws Exception {
        Properties props=new Properties();
        props.setProperty("jcifs.smb.client.minVersion","SMB202");
        props.setProperty("jcifs.smb.client.maxVersion","SMB311");
        props.setProperty("jcifs.smb.client.connTimeout","5000");
        props.setProperty("jcifs.smb.client.responseTimeout","15000");
        props.setProperty("jcifs.smb.client.soTimeout","15000");
        base=new BaseContext(new PropertyConfiguration(props));
        authenticated=base.withCredentials(p.user.isEmpty()?new NtlmPasswordAuthenticator():new NtlmPasswordAuthenticator(p.domain,p.user,p.password));
    }
    SmbFile file(String url) throws Exception { return new SmbFile(url,authenticated); }
    record Folder(String name,String url) {}
    List<Folder> folders(String url) throws Exception {
        List<Folder> result=new ArrayList<>();
        try(SmbFile parent=file(url)) {
            for(SmbFile child:parent.listFiles()) try(child) {
                String name=child.getName();
                if(child.isDirectory() && !name.equals("./") && !name.equals("../") && !name.equalsIgnoreCase("IPC$/"))
                    result.add(new Folder(name.replaceAll("/$",""),child.getCanonicalPath()));
            }
        }
        result.sort((a,b)->a.name.compareToIgnoreCase(b.name)); return result;
    }
    @Override public void close() throws Exception { authenticated.close(); base.close(); }
}
