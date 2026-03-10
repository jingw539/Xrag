package com.hospital.xray;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hospital.xray.mapper")
public class ChestXrayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChestXrayApplication.class, args);
    }
}
