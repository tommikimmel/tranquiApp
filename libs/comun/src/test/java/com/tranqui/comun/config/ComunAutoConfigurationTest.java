package com.tranqui.comun.config;

import com.tranqui.comun.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class ComunAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ComunAutoConfiguration.class));

    @Test
    void autoConfiguration_registraFiltroEnContextoWeb() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CorrelationIdFilter.class);
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
    }
}
