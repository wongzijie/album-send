package cn.album.send;
import java.nio.charset.StandardCharsets;
final class Names {
    static String safe(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        n = n.replaceAll("[. ]+$", "");
        if (n.isEmpty() || n.equals(".") || n.equals("..")) n="media";
        int dot=n.lastIndexOf('.');
        String stem=dot>0?n.substring(0,dot):n;
        if(stem.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")) n="_"+n;
        if(n.getBytes(StandardCharsets.UTF_8).length>220) {
            dot=n.lastIndexOf('.');String ext=dot>0 && n.length()-dot<16?n.substring(dot):"";
            String body=ext.isEmpty()?n:n.substring(0,dot);StringBuilder trimmed=new StringBuilder();
            int budget=220-ext.getBytes(StandardCharsets.UTF_8).length;
            for(int cp:body.codePoints().toArray()) {String piece=new String(Character.toChars(cp));int bytes=piece.getBytes(StandardCharsets.UTF_8).length;if(bytes>budget)break;trimmed.append(piece);budget-=bytes;}
            n=trimmed+ext;
        }
        return n;
    }
    static String numbered(String name, int n) {
        if(n==0) return name;
        int dot=name.lastIndexOf('.');
        return dot>0 ? name.substring(0,dot)+" ("+n+")"+name.substring(dot) : name+" ("+n+")";
    }
}
