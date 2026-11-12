package com.focus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.focus.mapper")
public class FocusApplication {

    public static void main(String[] args) {
        SpringApplication.run(FocusApplication.class, args);
    }
}
