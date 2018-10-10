package com.atlassian.forexample;

import com.atlassian.forexample.processing.ExampleProcessor;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.reactivestreams.Publisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class ForExampleHandler {

    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
            options -> options.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .compression(true)
                    .afterNettyContextInit(ctx -> {
                        ctx.addHandlerLast(new ReadTimeoutHandler(2000, TimeUnit.MILLISECONDS));
                    }));

    WebClient webClient = WebClient.builder()
            .clientConnector(connector)
            .build();

    public Mono<ServerResponse> getExampleLines(ServerRequest request) {
        return getExampleContentImpl(MediaType.TEXT_PLAIN, request.queryParam("url"), reader -> ExampleProcessor.extractContentAsLines(reader, mkOptions(request)));
    }

    public Mono<ServerResponse> getExampleJson(ServerRequest request) {
        return getExampleContentImpl(MediaType.APPLICATION_JSON, request.queryParam("url"), reader -> ExampleProcessor.extractContentAsJson(reader, mkOptions(request)));
    }

    private Mono<ServerResponse> getExampleContentImpl(MediaType contentType, Optional<String> url, Function<InputStreamReader, Publisher<? extends String>> extractorFunction) {
        if (!url.isPresent()) {
            return badRequest().syncBody("You must provide a URL");
        }
        Flux<String> dataBuffers = extractExampleContent(url.get(), extractorFunction);

        return ok()
                .contentType(contentType)
                .body(BodyInserters.fromPublisher(dataBuffers, String.class))
                .onErrorMap(RuntimeException.class, e -> new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage()));
    }

    private ExampleProcessor.Options mkOptions(ServerRequest request) {
        return new ExampleProcessor.Options()
                .figure(request.queryParam("figure"))
                .prefix(request.queryParam("prefix"));
    }

    private Flux<String> extractExampleContent(String url, Function<InputStreamReader, Publisher<? extends String>> extractor) {

        URI uri = buildUri(url);
        return webClient.get().uri(uri).exchange()
                .metrics()
                .flatMap(this::mkInputStreamResource)
                .map(this::mkReader)
                .flux()
                .flatMap(extractor);
    }

    private Mono<InputStreamResource> mkInputStreamResource(ClientResponse clientResponse) {
        if (!clientResponse.statusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return clientResponse.bodyToMono(InputStreamResource.class);
    }


    private InputStreamReader mkReader(InputStreamResource isResource) {
        try {
            return new InputStreamReader(isResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private URI buildUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


}
