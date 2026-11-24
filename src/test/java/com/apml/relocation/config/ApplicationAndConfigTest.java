package com.apml.relocation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.ApmlApplication;
import org.junit.jupiter.api.Test;

class ApplicationAndConfigTest {

    private final AppConfig config = new AppConfig();

    @Test
    void clockRunsInIndianStandardTime() {
        assertThat(config.clock().getZone()).isEqualTo(AppConfig.IST);
    }

    @Test
    void openApiDescribesTheService() {
        assertThat(config.apmlOpenApi().getInfo().getTitle()).contains("APML");
    }

    @Test
    void applicationStartsWithoutAWebServer() {
        assertThat(new ApmlApplication()).isNotNull();
        ApmlApplication.main(new String[]{"--spring.main.web-application-type=none", "--spring.profiles.active=dev",
                "--spring.datasource.url=jdbc:h2:mem:maintest;DB_CLOSE_DELAY=-1", "--apml.seed-enabled=false"});
        assertThat(config.clock()).isNotNull();
    }
}
