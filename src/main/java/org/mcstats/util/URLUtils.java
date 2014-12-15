package org.mcstats.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class URLUtils {

    /**
     * URL decode a string as UTF-8
     *
     * @param data
     * @return
     */
    public static String decode(String data) {
        try {
            return URLDecoder.decode(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return data; // Unsuppoted
        }
    }

}
