package com.transakt.transakt;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class TransaktApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransaktApplication.class, args);
	}


}