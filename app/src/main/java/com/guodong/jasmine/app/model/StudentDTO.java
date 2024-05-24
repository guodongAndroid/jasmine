package com.guodong.jasmine.app.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.guodong.jasmine.Jasmine;
import lombok.*;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
@Jasmine(enabled = true, sqlEscaping = true, useOriginalVariableName = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class StudentDTO {

    private static final String TAG = "StudentDTO";

    @NonNull
    @TableField(value = "00int", exist = false)
    private String name;

    @TableField("age")
    private int age;

    @TableField("address")
    private String address;

    @TableField("score")
    private float score;
}
