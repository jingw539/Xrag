package com.hospital.xray;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan("com.hospital.xray.mapper")
public class ChestXrayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChestXrayApplication.class, args);
    }
}
