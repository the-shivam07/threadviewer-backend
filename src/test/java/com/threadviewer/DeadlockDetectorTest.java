
package com.threadviewer;

import com.threadviewer.analysis.DeadlockDetector;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeadlockDetectorTest {

    @Test
    void shouldDetectDeadlock() {

        Map<String, String> waitingFor = new HashMap<>();

        waitingFor.put("Thread-A", "Thread-B");
        waitingFor.put("Thread-B", "Thread-A");

        DeadlockDetector detector = new DeadlockDetector();

        assertTrue(detector.hasDeadlock(waitingFor));
    }

    @Test
    void shouldReturnFalseWhenNoDeadlock() {

        Map<String, String> waitingFor = new HashMap<>();

        waitingFor.put("Thread-A", "Thread-B");
        waitingFor.put("Thread-B", null);

        DeadlockDetector detector = new DeadlockDetector();

        assertFalse(detector.hasDeadlock(waitingFor));
    }

    @Test
    void shouldReturnFalseWhenGraphIsEmpty() {

        Map<String, String> waitingFor = new HashMap<>();

        DeadlockDetector detector = new DeadlockDetector();

        assertFalse(detector.hasDeadlock(waitingFor));
    }

    @Test
    void shouldDetectDeadlockAmongThreeThreads() {

        Map<String, String> waitingFor = new HashMap<>();

        waitingFor.put("Thread-A", "Thread-B");
        waitingFor.put("Thread-B", "Thread-C");
        waitingFor.put("Thread-C", "Thread-A");

        DeadlockDetector detector = new DeadlockDetector();

        assertTrue(detector.hasDeadlock(waitingFor));
    }

    @Test
    void shouldBuildWaitingGraphCorrectly() {

        Map<String, String> lockOwners = new HashMap<>();
        lockOwners.put("lock-1", "Thread-B");
        lockOwners.put("lock-2", "Thread-C");

        Map<String, String> threadWaitingLocks = new HashMap<>();
        threadWaitingLocks.put("Thread-A", "lock-1");
        threadWaitingLocks.put("Thread-B", "lock-2");

        DeadlockDetector detector = new DeadlockDetector();

        Map<String, String> waitingFor =
                detector.buildWaitingGraph(
                        lockOwners,
                        threadWaitingLocks
                );

        assertEquals(2, waitingFor.size());
        assertEquals("Thread-B", waitingFor.get("Thread-A"));
        assertEquals("Thread-C", waitingFor.get("Thread-B"));
    }

    @Test
    void shouldIgnoreLockWhenOwnerIsUnknown() {

        Map<String, String> lockOwners = new HashMap<>();
        lockOwners.put("lock-1", "Thread-B");

        Map<String, String> threadWaitingLocks = new HashMap<>();
        threadWaitingLocks.put("Thread-A", "unknown-lock");

        DeadlockDetector detector = new DeadlockDetector();

        Map<String, String> waitingFor =
                detector.buildWaitingGraph(
                        lockOwners,
                        threadWaitingLocks
                );

        assertTrue(waitingFor.isEmpty());
    }

}
