package com.agile.team;

import com.agile.team.infrastructure.config.SdlcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SdlcProperties.class)
// Drives StageWorker, which polls the durable stage queue.
@org.springframework.scheduling.annotation.EnableScheduling
public class TeamApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamApplication.class, args);
	}

}
