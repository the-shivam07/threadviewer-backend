
package com.threadviewer.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MemoryDumpParser {

    private static final Pattern MEMORY_PATTERN =
            Pattern.compile(
                    "(Heap Used|Heap Committed|Heap Max):\\s*(\\d+)\\s*MB",
                    Pattern.CASE_INSENSITIVE
            );

    public void parse(String filePath) throws IOException {

        for (String line : Files.readAllLines(Path.of(filePath))) {

            Matcher matcher = MEMORY_PATTERN.matcher(line);

            if (matcher.find()) {
                String metricName = matcher.group(1);
                long memoryValue = Long.parseLong(matcher.group(2));

                System.out.println(
                        metricName + " = " + memoryValue + " MB"
                );
            }
        }
    }
}
