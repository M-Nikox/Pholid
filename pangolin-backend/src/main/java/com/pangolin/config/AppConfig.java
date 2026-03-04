/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.config;

import com.pangolin.client.FlamencoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Creates the RestClient bean (replacing the deprecated RestTemplate) and wires
 * up the declarative FlamencoClient via @HttpExchange proxy.
 *
 * Timeouts and base URL come from PangolinProperties, no hardcoded values here.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(PangolinProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.http().connectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.http().readTimeout()));

        return RestClient.builder()
                .baseUrl(props.manager().url())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public FlamencoClient flamencoClient(RestClient restClient) {
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(FlamencoClient.class);
    }
}