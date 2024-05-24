package com.guodong.jasmine.app;

import com.guodong.jasmine.app.model.StudentDTO;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
public class App {

    public static void main(String[] args) {
        StudentDTO dto = new StudentDTO("guodongAndroid");
        System.out.println(dto.getName());
        System.out.println(StudentDTO.NAME);
    }
}
