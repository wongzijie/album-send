package cn.album.send;
import android.net.Uri;
import org.json.JSONObject;
final class MediaItem {
    final Uri uri; final String name, album; final long size, duration; final boolean video;
    MediaItem(Uri uri, String name, String album, long size, long duration, boolean video) {
        this.uri=uri; this.name=name; this.album=album; this.size=size; this.duration=duration; this.video=video;
    }
    JSONObject json() throws Exception { return new JSONObject().put("uri",uri.toString()).put("name",name).put("album",album).put("size",size).put("duration",duration).put("video",video); }
    static MediaItem from(JSONObject j) throws Exception { return new MediaItem(Uri.parse(j.getString("uri")),j.getString("name"),j.optString("album"),j.optLong("size"),j.optLong("duration"),j.optBoolean("video")); }
}
