package com.example.genai.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class GeminiWebClientConfig {

    @Value("${gemini.apiKey}")
    private String apiKey;

    @Value("${gemini.ssl.trustAll:false}")
    private boolean trustAll;

    // e.g. 10MB; increase if you generate longer audio
    @Value("${gemini.webclient.maxInMemorySizeBytes:10485760}")
    private int maxInMemorySizeBytes;

    @Bean(name = "geminiWebClient")
    public WebClient geminiWebClient() throws Exception {

        System.out.println("[GeminiWebClientConfig] trustAll=" + trustAll
                + ", maxInMemorySizeBytes=" + maxInMemorySizeBytes);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .exchangeStrategies(strategies)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (!trustAll) {
            return builder.build();
        }

        // DEV ONLY: trust-all
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(ssl -> ssl.sslContext(sslContext));

        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
