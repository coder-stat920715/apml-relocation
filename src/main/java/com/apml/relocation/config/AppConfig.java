package com.apml.relocation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class AppConfig {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Single time source (IST) so every date rule is deterministic and testable. */
    @Bean
    public Clock clock() {
        return Clock.system(IST);
    }

    @Bean
    public OpenAPI apmlOpenApi() {
        return new OpenAPI().info(new Info()
                .title("APML Consignment & Relocation Management API")
                .version("1.0.0")
                .description("Enquiry, quotation, payments, compliance, transit and claims for APML consignments."));
    }
}
