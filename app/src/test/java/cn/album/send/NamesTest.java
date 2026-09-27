package cn.album.send;
import org.junit.Test;
import static org.junit.Assert.*;
public class NamesTest {
    @Test public void keepsUnicodeAndOriginalExtension(){assertEquals("旅行 照片.heic",Names.safe("旅行 照片.heic"));assertEquals("旅行 照片 (2).heic",Names.numbered("旅行 照片.heic",2));}
    @Test public void blocksPathTraversalAndWindowsInvalidNames(){assertEquals("media",Names.safe(".."));assertEquals(".._.._private",Names.safe("../../private"));assertEquals("a_b_c_.jpg",Names.safe("a:b?c*.jpg"));assertEquals("photo",Names.safe("photo. "));}
    @Test public void respectsUtf8FileNameLimit(){String name=Names.safe("旅行".repeat(100)+".jpg");assertTrue(name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=220);assertTrue(name.endsWith(".jpg"));assertEquals("_CON.jpg",Names.safe("CON.jpg"));}
    @Test public void collisionsAlsoWorkWithoutExtension(){assertEquals("file (1)",Names.numbered("file",1));assertEquals(".hidden (1)",Names.numbered(".hidden",1));assertEquals("clip.mp4",Names.numbered("clip.mp4",0));}
}
