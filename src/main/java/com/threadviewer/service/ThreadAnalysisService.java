
package com.threadviewer.service;

import com.threadviewer.analysis.DeadlockDetector;
import com.threadviewer.dto.ThreadAnalysisResult;
import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadInfo;
import com.threadviewer.entity.AnalysisResult;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.repository.AnalysisResultRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ThreadAnalysisService {

    private final ThreadDumpParser parser;
    private final DeadlockDetector deadlockDetector;
    private final AnalysisResultRepository analysisResultRepository;

    // Backward-compatible constructor for existing unit tests.
    public ThreadAnalysisService(ThreadDumpParser parser) {
        this(parser, null);
    }

    // Spring Boot uses this constructor for dependency injection.
    @Autowired
    public ThreadAnalysisService(
            ThreadDumpParser parser,
            AnalysisResultRepository analysisResultRepository) {

        this.parser = parser;
        this.deadlockDetector = new DeadlockDetector();
        this.analysisResultRepository = analysisResultRepository;
    }

    // Analyze parsed thread dump data.
    public ThreadAnalysisResult analyze(ThreadDumpData dump) {

        List<ThreadInfo> threads = dump.getThreads();

        int total = threads.size();
        int daemon = 0;
        int nonDaemon = 0;

        for (ThreadInfo thread : threads) {
            if (thread.isDaemon()) {
                daemon++;
            } else {
                nonDaemon++;
            }
        }

        List<ThreadInfo> blocked = threads.stream()
                .filter(t -> "BLOCKED".equals(t.getState()))
                .toList();

        List<ThreadInfo> waiting = threads.stream()
                .filter(t -> "WAITING".equals(t.getState()))
                .toList();

        // Map each lock to its owning thread.
        Map<String, String> lockOwners = new HashMap<>();

        for (ThreadInfo thread : threads) {
            for (String lock : thread.getOwnedLocks()) {
                lockOwners.put(lock, thread.getName());
            }
        }

        // Map each thread to the lock it is waiting for.
        Map<String, String> threadWaitingLocks = new HashMap<>();

        for (ThreadInfo thread : threads) {
            String waitingLock = thread.getWaitingForLock();

            if (waitingLock != null) {
                threadWaitingLocks.put(
                        thread.getName(),
                        waitingLock
                );
            }
        }

        // Build the waiting-thread graph.
        Map<String, String> waitingFor =
                deadlockDetector.buildWaitingGraph(
                        lockOwners,
                        threadWaitingLocks
                );

        boolean deadlockDetected =
                deadlockDetector.hasDeadlock(waitingFor);

        List<String> deadlockedThreads =
                findDeadlockedThreads(waitingFor);

        ThreadAnalysisResult result = new ThreadAnalysisResult(
                total,
                daemon,
                nonDaemon,
                blocked,
                waiting,
                deadlockDetected,
                deadlockedThreads
        );

        // Save the analysis summary to PostgreSQL.
        saveAnalysis(result);

        return result;
    }

    // Persist the analysis summary.
    @Transactional
    public void saveAnalysis(ThreadAnalysisResult result) {

        // Allows existing unit tests to use the one-argument constructor.
        if (analysisResultRepository == null) {
            return;
        }

        AnalysisResult entity = new AnalysisResult();

        entity.setTotalThreads(result.getTotalThreads());
        entity.setDaemonThreads(result.getDaemonThreads());
        entity.setNonDaemonThreads(result.getNonDaemonThreads());

        entity.setBlockedThreadCount(
                result.getBlockedThreads().size()
        );

        entity.setWaitingThreadCount(
                result.getWaitingThreads().size()
        );

        entity.setDeadlockDetected(
                result.isDeadlockDetected()
        );

        analysisResultRepository.save(entity);
    }

    // Find threads involved in deadlock cycles.
    private List<String> findDeadlockedThreads(
            Map<String, String> waitingFor) {

        Set<String> deadlocked = new HashSet<>();

        for (String start : waitingFor.keySet()) {

            Map<String, Integer> visitedAt = new HashMap<>();
            List<String> path = new ArrayList<>();
            String current = start;

            while (current != null && waitingFor.containsKey(current)) {

                if (visitedAt.containsKey(current)) {
                    int cycleStart = visitedAt.get(current);

                    for (int i = cycleStart; i < path.size(); i++) {
                        deadlocked.add(path.get(i));
                    }

                    break;
                }

                visitedAt.put(current, path.size());
                path.add(current);
                current = waitingFor.get(current);
            }
        }

        return new ArrayList<>(deadlocked);
    }

    // Parse a thread dump file and analyze it.
    public ThreadAnalysisResult analyzeFile(String filePath)
            throws IOException {

        ThreadDumpData dump = parser.parse(filePath);
        return analyze(dump);
    }
}
