package com.guodong.jasmine.compiler;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
public final class Constants {

    /**
     * Mybatis-Plus
     */
    public static final class MP {
        public static final String FN_TABLE_FIELD = "com.baomidou.mybatisplus.annotation.TableField";
        public static final String VALUE_LITERAL = "value";
    }

    public static final class Java {

        private static final Pattern NAMED_PATTERN = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");

        private static final String[] KEYWORDS = {
                "goto", "const",
                "public", "private", "protected", "abstract",
                "class", "interface", "enum",
                "extends", "implements",
                "new", "super", "this",
                "instanceof",
                "byte", "short", "int", "long", "float", "double", "char", "boolean",
                "void", "null", "true", "false",
                "synchronized", "volatile",
                "throw", "throws", "try", "catch", "finally",
                "return",
                "if", "else", "for", "switch", "case", "break", "default", "continue", "while", "do", "yield",
                "package", "import",
                "transient",
                "assert",
                "native",
                "final", "static",
                "strictfp",
                "var", "record",
                "non-sealed", "sealed", "permits"
        };

        public static boolean isLegalNaming(String cs) {
            for (String keyword : KEYWORDS) {
                if (keyword.equals(cs.toLowerCase(Locale.ENGLISH))) {
                    return false;
                }
            }
            return NAMED_PATTERN.matcher(cs).matches();
        }
    }

}
