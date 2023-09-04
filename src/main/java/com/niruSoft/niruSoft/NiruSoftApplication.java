package com.niruSoft.niruSoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NiruSoftApplication {

	public static void main(String[] args) {
		SpringApplication.run(NiruSoftApplication.class, args);
	}

}
