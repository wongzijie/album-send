package cn.album.send;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jcifs.smb.SmbFile;
public class SmbIntegrationTest {
    private SmbClient client() throws Exception {Assume.assumeTrue("Run tools/test-smb.sh to enable localhost SMB integration", "1".equals(System.getenv("ALBUM_SMB_TEST")));ShareProfile p=new ShareProfile();p.host="127.0.0.1:1445";p.share="MEDIA";p.user="albumtest";p.password="local-test-only";return new SmbClient(p);}
    @Test public void browsesUnicodeAndSpecialCharacterSubdirectories() throws Exception {
        try(SmbClient c=client()) {
            List<SmbClient.Folder> folders=c.folders("smb://127.0.0.1:1445/MEDIA/");
            SmbClient.Folder folder=folders.stream().filter(f->f.name().equals("旅行 + #100%" )).findFirst().orElseThrow();
            assertTrue(folder.url().endsWith("/旅行 + #100%/"));
            assertEquals("原片",c.folders(folder.url()).get(0).name());
        }
    }
    @Test public void writesOriginalBytesAndNeverReplacesAnExistingFile() throws Exception {
        try(SmbClient c=client()) {
            String folder="smb://127.0.0.1:1445/MEDIA/旅行 + #100%/原片/";
            String id=UUID.randomUUID().toString();
            byte[] bytes=new byte[600000];new Random(123).nextBytes(bytes);
            try(SmbFile temp=c.file(folder+id+".part");SmbFile dest=c.file(folder+id+"照片 + #100%.heic");SmbFile other=c.file(folder+id+"other.part")) {
                temp.createNewFile();try(OutputStream out=temp.getOutputStream()){out.write(bytes);}
                assertEquals(bytes.length,temp.length());temp.renameTo(dest,false);assertFalse(temp.exists());
                try(InputStream in=dest.getInputStream()){assertArrayEquals(bytes,in.readAllBytes());}
                other.createNewFile();try(OutputStream out=other.getOutputStream()){out.write("different".getBytes(StandardCharsets.UTF_8));}
                try{other.renameTo(dest,false);fail("Must reject replacing an existing destination");}catch(jcifs.smb.SmbException expected){}
                try(InputStream in=dest.getInputStream()){assertArrayEquals(bytes,in.readAllBytes());}
                other.delete();dest.delete();
            }
        }
    }
    @Test public void rejectsWrongCredentials() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("ALBUM_SMB_TEST")));
        ShareProfile p=new ShareProfile();p.host="127.0.0.1:1445";p.user="albumtest";p.password="wrong";
        try(SmbClient c=new SmbClient(p)) {try{c.folders("smb://127.0.0.1:1445/MEDIA/");fail("Must reject invalid password");}catch(jcifs.smb.SmbException expected){}}
    }
}
