package com.af.novadesk.api;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories(basePackages = {
        "com.af.novadesk.api.identity.repository",
        "com.af.novadesk.api.finance",
        "com.af.novadesk.api.payroll"
})
public class NovaDeskapiApplication {


    public static void main(String[] args) {
        SpringApplication.run(NovaDeskapiApplication.class, args);
    }
}
