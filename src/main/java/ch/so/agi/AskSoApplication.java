package ch.so.agi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AskSoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AskSoApplication.class, args);
    }

}
