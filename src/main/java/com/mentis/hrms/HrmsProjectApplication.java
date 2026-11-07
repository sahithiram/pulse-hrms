package com.mentis.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = "com.mentis.hrms")
@EnableScheduling // ✅ Must have this
@EnableAsync
public class HrmsProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrmsProjectApplication.class, args);
    }
}