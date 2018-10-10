package com.atlassian.forexample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class Routes {

    @Bean
    public RouterFunction<ServerResponse> route(ForExampleHandler personHandler) {
        return RouterFunctions
                .route(GET("/forexample"), personHandler::getExampleLines)
                .andRoute(GET("/forexample.json"), personHandler::getExampleJson);
    }
}
