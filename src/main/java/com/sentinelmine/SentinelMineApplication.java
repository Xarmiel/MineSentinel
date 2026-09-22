package com.sentinelmine;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class SentinelMineApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SentinelMineApplication.class)
                .headless(false)
                .run(args);
    }
}
