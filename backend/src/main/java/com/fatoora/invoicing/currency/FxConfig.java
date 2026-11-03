package com.fatoora.invoicing.currency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class FxConfig {

    /** Off in tests (no network); on by default elsewhere. */
    @Bean
    @ConditionalOnProperty(name = "app.fx.ecb-enabled", havingValue = "true", matchIfMissing = true)
    EcbRates ecbRates(@Value("${app.fx.ecb-url:https://api.frankfurter.dev}") String url, JsonMapper json) {
        return new EcbRates(url, json);
    }
}
