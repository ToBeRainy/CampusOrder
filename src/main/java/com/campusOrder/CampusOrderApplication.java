package com.campusOrder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.campusOrder.mapper")
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class CampusOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusOrderApplication.class, args);
    }
}
