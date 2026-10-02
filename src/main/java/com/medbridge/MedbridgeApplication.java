package com.medbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point.
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan.
 * Component scanning starts from this package, so every class under com.medbridge
 * annotated with @Component / @Service / @Repository / @RestController becomes a bean.
 */
@SpringBootApplication
public class MedbridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedbridgeApplication.class, args);
    }
}
