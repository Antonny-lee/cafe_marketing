package com.cafe.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CafeDashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(CafeDashboardApplication.class, args);
	}

}
