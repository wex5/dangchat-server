package im.actor.server.api.http.util;

/**
 * Created by User on 2017/2/27.
 */
public class MessageContentUtils {

    public static String getMessageContent(byte[] content, int contentType) {
        byte[] bs = new byte[content.length - 6];
        for(int i = 6; i < content.length; i++) {
            bs[i - 6] = content[i];
        }
        try {
            String result = new String(bs, "utf-8");
            //contentType:1-Text, 2-Tips, 3-Photo and file
            if(contentType == 1 || contentType == 2) {
                return dealString4JSON(result);
            } else {
                return result;
            }
        } catch(java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }


    private static String dealString4JSON(String ors) {
        ors = ors == null ? "" : ors;
        StringBuffer buffer = new StringBuffer(ors);
        int i = 0;
        while (i < buffer.length()) {
            if (buffer.charAt(i) == '\'' || buffer.charAt(i) == '\\') {
                buffer.insert(i, '\\');
                i += 2;
            } else {
                i++;
            }
        }
        return buffer.toString().replaceAll("\r", "").replaceAll("\n", "");
    }

}
