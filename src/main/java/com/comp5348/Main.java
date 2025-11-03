package com.comp5348;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication
@EntityScan("com.comp5348")
@EnableJpaRepositories("com.comp5348")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
