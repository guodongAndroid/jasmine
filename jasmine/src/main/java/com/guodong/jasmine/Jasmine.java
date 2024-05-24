package com.guodong.jasmine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为所标记类中的成员变量生成公开常量
 *
 * <p>
 * Created by guodongAndroid on 2024/5/24.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Jasmine {

    /**
     * 是否启用, 默认启用
     */
    boolean enabled() default true;

    /**
     * 常量的值是否需要SQL转义, 默认不需要
     */
    boolean sqlEscaping() default false;

    /**
     * 是否使用成员变量原始命名作为生成的常量的命名, 默认是
     */
    boolean useOriginalVariableName() default true;
}
