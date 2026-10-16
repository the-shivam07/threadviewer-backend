
package com.threadviewer;

import com.threadviewer.dto.ThreadAnalysisResult;
import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadInfo;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.service.ThreadAnalysisService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ThreadAnalysisServiceTest {

    @Test
    void shouldAnalyzeThreadsAndDetectDeadlock() {

        // Thread 1: main
        ThreadInfo thread1 = new ThreadInfo(
                "main", 1, 5,
                "WAITING", "sample stack", false
        );

        // Thread 2: Worker-1
        ThreadInfo thread2 = new ThreadInfo(
                "Worker-1", 2, 5,
                "BLOCKED", "sample stack", false
        );
        thread2.setWaitingForLock("0x00000001");
        thread2.setOwnedLocks(List.of("0x00000002"));

        // Thread 3: Worker-2
        ThreadInfo thread3 = new ThreadInfo(
                "Worker-2", 3, 5,
                "BLOCKED", "sample stack", false
        );
        thread3.setWaitingForLock("0x00000002");
        thread3.setOwnedLocks(List.of("0x00000001"));

        // Create thread dump
        ThreadDumpData dump = new ThreadDumpData(
                "2026-10-16",
                "Java HotSpot JVM",
                List.of(thread1, thread2, thread3)
        );

        // Create service
        ThreadAnalysisService service =
                new ThreadAnalysisService(new ThreadDumpParser());

        // Analyze thread dump
        ThreadAnalysisResult result = service.analyze(dump);

        // Verify thread statistics
        assertEquals(3, result.getTotalThreads());
        assertEquals(0, result.getDaemonThreads());
        assertEquals(3, result.getNonDaemonThreads());

        // Verify thread states: total counts
        assertEquals(2, result.getBlockedThreads().size());
        assertEquals(1, result.getWaitingThreads().size());

        // Verify exact BLOCKED threads
        assertEquals(
                Set.of("Worker-1", "Worker-2"),
                result.getBlockedThreads()
                        .stream()
                        .map(ThreadInfo::getName)
                        .collect(Collectors.toSet())
        );

        // Verify exact WAITING threads
        assertEquals(
                Set.of("main"),
                result.getWaitingThreads()
                        .stream()
                        .map(ThreadInfo::getName)
                        .collect(Collectors.toSet())
        );

        // Verify deadlock detection
        assertTrue(result.isDeadlockDetected());

        // Verify exact deadlocked threads
        assertEquals(
                Set.of("Worker-1", "Worker-2"),
                Set.copyOf(result.getDeadlockedThreads())
        );
    }

    @Test
    void shouldNotDetectDeadlockWhenNoCycleExists() {

        // Worker-1 waits for a lock owned by Worker-2
        ThreadInfo thread1 = new ThreadInfo(
                "Worker-1", 1, 5,
                "BLOCKED", "sample stack", false
        );
        thread1.setWaitingForLock("lock-1");

        // Worker-2 owns the lock but is not waiting for another lock
        ThreadInfo thread2 = new ThreadInfo(
                "Worker-2", 2, 5,
                "RUNNABLE", "sample stack", false
        );
        thread2.setOwnedLocks(List.of("lock-1"));

        ThreadDumpData dump = new ThreadDumpData(
                "2026-10-16",
                "Java HotSpot JVM",
                List.of(thread1, thread2)
        );

        ThreadAnalysisService service =
                new ThreadAnalysisService(new ThreadDumpParser());

        ThreadAnalysisResult result = service.analyze(dump);

        // Verify thread statistics and states
        assertEquals(2, result.getTotalThreads());
        assertEquals(1, result.getBlockedThreads().size());
        assertEquals(0, result.getWaitingThreads().size());

        // Verify no deadlock exists
        assertFalse(result.isDeadlockDetected());
        assertTrue(result.getDeadlockedThreads().isEmpty());
    }
}
