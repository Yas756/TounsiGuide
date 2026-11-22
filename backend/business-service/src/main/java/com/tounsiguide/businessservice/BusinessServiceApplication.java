package com.tounsiguide.businessservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Business Service (Sprint 3): verified business pages linked to an existing Location, page members,
 * page requests decided by an administrator, and the page wall (posts confirmed for the Location, composed
 * from the Location and Community services, never copied).
 */
@SpringBootApplication
public class BusinessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessServiceApplication.class, args);
    }
}
