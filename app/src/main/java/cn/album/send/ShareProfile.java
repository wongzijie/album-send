package cn.album.send;
import org.json.JSONObject;
final class ShareProfile {
    String id=java.util.UUID.randomUUID().toString(), label="", host="", share="", user="", password="", domain="", path="";
    String root() { return "smb://"+host+"/"+(share.isEmpty()?"":share+"/"); }
    String url() { return root()+path; }
    JSONObject json() throws Exception { return new JSONObject().put("id",id).put("label",label).put("host",host).put("share",share).put("user",user).put("password",password).put("domain",domain).put("path",path); }
    static ShareProfile from(JSONObject j) throws Exception { ShareProfile p=new ShareProfile(); p.id=j.getString("id"); p.label=j.optString("label"); p.host=j.getString("host"); p.share=j.optString("share"); p.user=j.optString("user"); p.password=j.optString("password"); p.domain=j.optString("domain"); p.path=j.optString("path"); return p; }
}
