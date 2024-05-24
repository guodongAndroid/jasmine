package com.guodong.jasmine.app.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.guodong.jasmine.Ignore;
import com.guodong.jasmine.Jasmine;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
@Jasmine(enabled = true, sqlEscaping = true, useOriginalVariableName = false)
public class User {

    @TableField("user_name")
    private String name;

    private String parentName;

    private String phoneNumber;

    @Ignore
    private byte aByte;

    @Ignore
    private char aChar;

}
