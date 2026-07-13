package com.costco.foundation.integration.framework.gcp.scaling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ScalingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalingApplication.class, args);
	}
}
