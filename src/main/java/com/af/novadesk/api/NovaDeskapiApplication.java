package com.af.novadesk.api;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


@SpringBootApplication
@ConfigurationPropertiesScan
public class NovaDeskapiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovaDeskapiApplication.class, args);
    }
}
