package com.atlassian.forexample.processing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;

public class ExampleProcessor {

    public static class Options {
        Optional<String> figure = Optional.empty();

        String startPrefix = "::";
        String endPrefix = "::/";

        public Options figure(String figure) {
            return figure(Optional.ofNullable(figure));
        }

        public Options figure(Optional<String> figure) {
            this.figure = figure;
            return this;
        }

        public Options prefix(String prefix) {
            return prefix(Optional.ofNullable(prefix));
        }

        public Options prefix(Optional<String> prefix) {
            this.startPrefix = prefix.orElse(this.startPrefix);
            this.endPrefix = prefix.orElse(this.startPrefix) + "/";
            return this;
        }
    }

    private static ContentProduction STRING_PRODUCTION = new ContentProduction() {
        @Override
        public void preamble(FluxSink<String> sink) {
        }

        @Override
        public void appendChar(int ch, StringBuilder line) {
            line.append((char) ch);
        }

        @Override
        public void epilogue(FluxSink<String> sink) {
        }
    };

    private static ContentProduction JSON_PRODUCTION = new ContentProduction() {
        @Override
        public void preamble(FluxSink<String> sink) {
            sink.next("{ \"example\" : \"");
        }

        @Override
        public void appendChar(int ch, StringBuilder line) {
            switch (ch) {
                case '"':
                    line.append('\\').append('"');
                    break;
                case '\\':
                    line.append('\\').append('\\');
                    break;
                case '\b':
                    line.append('\\').append('b');
                    break;
                case '\f':
                    line.append('\\').append('f');
                    break;
                case '\n':
                    line.append('\\').append('n');
                    break;
                case '\r':
                    line.append('\\').append('r');
                    break;
                case '\t':
                    line.append('\\').append('t');
                    break;
                default:
                    line.append((char) ch);
                    break;
            }
        }

        @Override
        public void epilogue(FluxSink<String> sink) {
            sink.next("\" }");
        }
    };

    private static final int MAX_READ_COUNT = 1024 * 1024 + 1;

    public static Flux<String> extractContentAsLines(Reader reader, Options options) {
        return extractContentImpl(reader, options, STRING_PRODUCTION);
    }

    public static Flux<String> extractContentAsJson(Reader reader, Options options) {
        return extractContentImpl(reader, options, JSON_PRODUCTION);
    }

    private static Flux<String> extractContentImpl(Reader reader, Options options, ContentProduction contentProduction) {
        return Flux.create(sink -> {
            StringBuilder line = new StringBuilder();
            boolean seenStartFigure = false;
            boolean seenEndFigure;

            Function<StringBuilder, Boolean> startPredicate = ln -> true;
            Function<StringBuilder, Boolean> endPredicate = ln -> false;
            if (options.figure.isPresent()) {
                String startMarker = options.startPrefix + options.figure.get();
                String endMarker = options.endPrefix + options.figure.get();
                startPredicate = ln -> ln.indexOf(startMarker) >= 0;
                endPredicate = ln -> ln.indexOf(endMarker) >= 0;
            } else {
                seenStartFigure = true;
            }


            contentProduction.preamble(sink);
            try (Reader rdr = reader) {
                int readCount = 0;
                int ch;
                while ((ch = rdr.read()) != -1) {
                    readCount++;
                    if (readCount > MAX_READ_COUNT) {
                        throw new RuntimeException("The example source exceeds the allowable limits");
                    }
                    contentProduction.appendChar(ch, line);
                    if (ch == '\n') {
                        if (!seenStartFigure) {
                            seenStartFigure = startPredicate.apply(line);
                            if (seenStartFigure) {
                                line.setLength(0);
                                continue;
                            }
                        }
                        if (seenStartFigure) {
                            seenEndFigure = endPredicate.apply(line);
                            if (seenEndFigure) {
                                line.setLength(0);
                                break;
                            }
                            // otherwise append the example content
                            sink.next(line.toString());
                        }
                        line.setLength(0);
                    }
                }
                //
                // did we have some left over text without an ending \n?
                if (line.length() > 0) {
                    sink.next(line.toString());
                }
                // and done
                contentProduction.epilogue(sink);
                sink.complete();
            } catch (IOException e) {
                sink.error(new UncheckedIOException(e.getMessage(), e));
            }
        });
    }

    interface ContentProduction {
        void preamble(FluxSink<String> sink);

        void appendChar(int ch, StringBuilder line);

        void epilogue(FluxSink<String> sink);
    }
}
