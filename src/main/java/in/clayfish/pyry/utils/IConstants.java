package in.clayfish.pyry.utils;

import java.text.SimpleDateFormat;

/**
 * @author shuklaalok7
 * @since 16/01/16
 */
public interface IConstants {
    String COLON = ":";
    String BLANK = "";
    String UNCHECKED = "unchecked";
    String MINUS = "-";
    String COMMA = ",";
    String SPACE = " ";
    String DOUBLE_QUOTES = "\"";

    SimpleDateFormat SIMPLE_DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd HH:mm:ss");

    long MB_24 = 24 * 1024 * 1024; // in bytes
    long MB_12 = 12 * 1024 * 1024; // in bytes
}
