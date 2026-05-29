package com.lastminute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LastMinuteApplication {

	public static void main(String[] args) {
		SpringApplication.run(LastMinuteApplication.class, args);
	}

}
