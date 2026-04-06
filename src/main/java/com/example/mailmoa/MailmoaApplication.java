package com.example.mailmoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MailmoaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MailmoaApplication.class, args);
	}

}
