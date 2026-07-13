package com.tranqui.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TranquiAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(TranquiAppApplication.class, args);
	}

}
