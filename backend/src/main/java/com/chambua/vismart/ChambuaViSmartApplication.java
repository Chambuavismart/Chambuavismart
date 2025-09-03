package com.chambua.vismart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChambuaViSmartApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChambuaViSmartApplication.class, args);
    }
}

