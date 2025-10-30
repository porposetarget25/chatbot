// src/main/java/com/example/genai/config/HttpConfig.java
package com.example.genai.config;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import javax.net.ssl.SSLException;

@Configuration
public class HttpConfig {

    @Bean
    @Profile("!insecure-ssl")
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    @Profile("insecure-ssl")
    public WebClient insecureWebClient() throws SSLException {
        var sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        var httpClient = HttpClient.create().secure(spec -> spec.sslContext(sslCtx));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
