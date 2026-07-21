package com.costco.foundation.integration.framework.gcp.scaling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.costco.foundation.integration.framework.gcp.scaling.configs.AuditRetentionProperties;
import com.costco.foundation.integration.framework.gcp.scaling.configs.UtilizationStreamProperties;

@EnableScheduling
@EnableConfigurationProperties({
        AuditRetentionProperties.class,
        UtilizationStreamProperties.class
})
@ConfigurationPropertiesScan
@SpringBootApplication
public class ScalingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalingApplication.class, args);
	}
}
