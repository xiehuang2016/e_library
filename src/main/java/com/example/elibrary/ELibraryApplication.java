package com.example.elibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableTransactionManagement
public class ELibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ELibraryApplication.class, args);
    }
}
