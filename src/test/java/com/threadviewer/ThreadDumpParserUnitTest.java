
package com.threadviewer;

import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadInfo;
import com.threadviewer.parser.ThreadDumpParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDumpParserUnitTest {

    @Test
    void shouldParseSampleThreadDump() throws Exception {

        // Arrange
        ThreadDumpParser parser = new ThreadDumpParser();

        String filePath = Path.of(
                "sample-dumps",
                "sample-thread-dump.txt"
        ).toString();

        // Act
        ThreadDumpData dump = parser.parse(filePath);

        // Verify basic parsing
        assertNotNull(dump);
        assertNotNull(dump.getThreads());
        assertEquals(3, dump.getThreads().size());

        // Verify thread names
        Set<String> threadNames = dump.getThreads()
                .stream()
                .map(ThreadInfo::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("main", "Worker-1", "Worker-2"),
                threadNames
        );

        // Verify thread states
        assertEquals(
                "WAITING",
                findThread(dump, "main").getState()
        );

        assertEquals(
                "BLOCKED",
                findThread(dump, "Worker-1").getState()
        );

        assertEquals(
                "BLOCKED",
                findThread(dump, "Worker-2").getState()
        );

        // Verify Worker-1 lock information
        assertEquals(
                "0x00000001",
                findThread(dump, "Worker-1").getWaitingForLock()
        );

        assertEquals(
                "0x00000002",
                findThread(dump, "Worker-1")
                        .getOwnedLocks()
                        .get(0)
        );

        // Verify Worker-2 lock information
        assertEquals(
                "0x00000002",
                findThread(dump, "Worker-2").getWaitingForLock()
        );

        assertEquals(
                "0x00000001",
                findThread(dump, "Worker-2")
                        .getOwnedLocks()
                        .get(0)
        );
    }

    @Test
    void shouldParseRealDeadlockThreadDump() throws Exception {

        // Arrange
        ThreadDumpParser parser = new ThreadDumpParser();

        String filePath = Path.of(
                "sample-dumps",
                "deadlock_stacktrace.txt"
        ).toString();

        // Act
        ThreadDumpData dump = parser.parse(filePath);

        // Verify total thread count
        assertNotNull(dump);
        assertNotNull(dump.getThreads());
        assertEquals(24, dump.getThreads().size());

        // Verify Thread-0
        ThreadInfo thread0 = findThread(dump, "Thread-0");

        assertEquals("BLOCKED", thread0.getState());
        assertNotNull(thread0.getWaitingForLock());
        assertFalse(thread0.getWaitingForLock().isBlank());
        assertFalse(thread0.getOwnedLocks().isEmpty());

        // Verify Thread-1
        ThreadInfo thread1 = findThread(dump, "Thread-1");

        assertEquals("BLOCKED", thread1.getState());
        assertNotNull(thread1.getWaitingForLock());
        assertFalse(thread1.getWaitingForLock().isBlank());
        assertFalse(thread1.getOwnedLocks().isEmpty());
    }

    private ThreadInfo findThread(
            ThreadDumpData dump,
            String threadName) {

        return dump.getThreads()
                .stream()
                .filter(thread ->
                        threadName.equals(thread.getName()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Thread not found: " + threadName
                        )
                );
    }
}
