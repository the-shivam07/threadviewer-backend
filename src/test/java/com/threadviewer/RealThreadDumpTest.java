
package com.threadviewer;

import com.threadviewer.dto.ThreadAnalysisResult;
import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.service.ThreadAnalysisService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealThreadDumpTest {

    @Test
    void shouldParseAndDetectDeadlockInRealThreadDump()
            throws IOException {

        String filePath = Path.of(
                "sample-dumps",
                "deadlock_stacktrace.txt"
        ).toString();

        ThreadDumpParser parser = new ThreadDumpParser();

        ThreadDumpData dump = parser.parse(filePath);

        ThreadAnalysisService service =
                new ThreadAnalysisService(parser);

        ThreadAnalysisResult result = service.analyze(dump);

        assertEquals(24, result.getTotalThreads());
        assertEquals(9, result.getDaemonThreads());
        assertEquals(15, result.getNonDaemonThreads());

        assertEquals(2, result.getBlockedThreads().size());

        assertTrue(result.isDeadlockDetected());

        assertEquals(
                Set.of("Thread-0", "Thread-1"),
                Set.copyOf(result.getDeadlockedThreads())
        );
    }
}
