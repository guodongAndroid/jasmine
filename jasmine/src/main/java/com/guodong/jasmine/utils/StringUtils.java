package com.guodong.jasmine.utils;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
public final class StringUtils {

    public static boolean isBlank(CharSequence cs) {
        if (cs != null) {
            int length = cs.length();

            for(int i = 0; i < length; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isEmptyOrBlank(CharSequence cs) {
        return isEmpty(cs) || isBlank(cs);
    }

    public static boolean isNotEmptyAndBlank(CharSequence cs) {
        return !isEmpty(cs) && !isBlank(cs);
    }

    public static boolean equals(String source, String target) {
        if (source == null) {
            return false;
        }

        return source.equals(target);
    }

}
