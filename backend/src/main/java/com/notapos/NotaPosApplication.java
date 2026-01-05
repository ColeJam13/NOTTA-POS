package com.notapos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nota-POS Main Application
 * 
 * Restaurant Point of Sale System
 * Built by Cole Jamison
 * 
 * @author CJ
 * @version 1.0.0
 */

@SpringBootApplication
@EnableScheduling 
public class NotaPosApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotaPosApplication.class, args);
    }
}