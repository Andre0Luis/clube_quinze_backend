package br.com.clube_quinze.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "br.com.clube_quinze.api.repository")
@EnableScheduling
public class ClubeQuinzeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClubeQuinzeApplication.class, args);
	}

}
