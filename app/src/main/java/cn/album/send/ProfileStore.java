package cn.album.send;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
final class ProfileStore {
    private final Context ctx;
    ProfileStore(Context ctx) {this.ctx=ctx;}
    private SecretKey key() throws Exception {
        KeyStore s=KeyStore.getInstance("AndroidKeyStore"); s.load(null);
        if(!s.containsAlias("album-share-profiles")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("album-share-profiles",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
        }
        return (SecretKey)s.getKey("album-share-profiles",null);
    }
    List<ShareProfile> load() throws Exception {
        String raw=ctx.getSharedPreferences("targets",0).getString("encrypted",null);
        List<ShareProfile> result=new ArrayList<>(); if(raw==null)return result;
        String[] pieces=raw.split(":"); Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)));
        JSONArray a=new JSONArray(new String(c.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8));
        for(int i=0;i<a.length();i++)result.add(ShareProfile.from(a.getJSONObject(i))); return result;
    }
    void save(List<ShareProfile> profiles) throws Exception {
        JSONArray a=new JSONArray(); for(ShareProfile p:profiles)a.put(p.json());
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=c.doFinal(a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!ctx.getSharedPreferences("targets",0).edit().putString("encrypted",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit()) throw new java.io.IOException("无法保存连接");
    }
}
