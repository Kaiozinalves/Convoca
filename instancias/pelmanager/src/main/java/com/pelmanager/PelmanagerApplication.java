package com.pelmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.pelmanager", "com.convoca.framework"})
@EntityScan(basePackages = {"com.pelmanager", "com.convoca.framework"})
@EnableJpaRepositories(basePackages = {"com.pelmanager", "com.convoca.framework"})
public class PelmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PelmanagerApplication.class, args);
	}

}
