package com.employeehub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers NotificationService's daily expiring-document
// check (see its @Scheduled method) — without this annotation somewhere in
// the application context, @Scheduled is silently inert.
@SpringBootApplication
@EnableScheduling
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SpringbootBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpringbootBackendApplication.class, args);
  }
}
