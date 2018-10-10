package com.atlassian.forexample.processing;

import com.atlassian.forexample.processing.ExampleProcessor;
import com.atlassian.forexample.processing.ExampleProcessor.Options;
import org.hamcrest.Matchers;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.Assert.assertThat;

public class ExampleProcessorTest {

    Reader read(String resource) {
        return new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/" + resource)));
    }

    String readStr(String resource) {
        try {
            URI uri = getClass().getResource("/" + resource).toURI();
            return new String(Files.readAllBytes(Paths.get(uri)));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String toString(Flux<String> flux) {
        return flux.toStream().collect(Collectors.joining(""));
    }

    @Test
    public void lineProduction() throws Exception {

        Reader data = read("example1");

        Options options = new Options().figure("FigureA").prefix("::");
        String output = toString(ExampleProcessor.extractContentAsLines(data, options));

        assertThat(output, Matchers.equalTo(readStr("expected_example1")));
    }

    @Test
    public void lineProductionNoFigure() throws Exception {

        Reader data = read("example1");

        Options options = new Options();
        String output = toString(ExampleProcessor.extractContentAsLines(data, options));

        assertThat(output, Matchers.equalTo(readStr("expected_example1NoFigure")));
    }

    @Test
    public void jsonProduction() throws Exception {

        Reader data = read("example1");

        Options options = new Options().figure("FigureA").prefix("::");
        String output = toString(ExampleProcessor.extractContentAsJson(data, options));

        assertThat(output, Matchers.equalTo(readStr("expected_example1.json")));
    }

}