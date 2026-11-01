package com.threadviewer.service;

import com.threadviewer.analysis.DeadlockDetector;
import com.threadviewer.dto.ThreadAnalysisResult;
import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadGroupSummary;
import com.threadviewer.dto.ThreadInfo;
import com.threadviewer.entity.AnalysisResult;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.repository.AnalysisResultRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ThreadAnalysisService {

    private final ThreadDumpParser parser;
    private final DeadlockDetector deadlockDetector;
    private final AnalysisResultRepository analysisResultRepository;

    public ThreadAnalysisService(ThreadDumpParser parser) {
        this(parser, null);
    }

    @Autowired
    public ThreadAnalysisService(
            ThreadDumpParser parser,
            AnalysisResultRepository analysisResultRepository) {

        this.parser = parser;
        this.deadlockDetector = new DeadlockDetector();
        this.analysisResultRepository = analysisResultRepository;
    }

    public ThreadAnalysisResult analyze(ThreadDumpData dump) {

        List<ThreadInfo> threads = dump.getThreads();

        // Calculate method frequency from captured stack traces.
        Map<String, Integer> methodFrequency =
                calculateMethodFrequency(threads);

        Map<String, Integer> threadStateCounts = threads.stream()
                .collect(Collectors.groupingBy(
                        thread -> normalizeState(thread.getState()),
                        Collectors.summingInt(thread -> 1)
                ));

        int total = threads.size();
        int daemon = 0;
        int nonDaemon = 0;
        int unknownDaemon = 0;

        for (ThreadInfo thread : threads) {
            Boolean daemonStatus = thread.isDaemon();

            if (Boolean.TRUE.equals(daemonStatus)) {
                daemon++;
            } else if (Boolean.FALSE.equals(daemonStatus)) {
                nonDaemon++;
            } else {
                unknownDaemon++;
            }
        }

        List<ThreadInfo> blocked = threads.stream()
                .filter(thread ->
                        "BLOCKED".equals(
                                normalizeState(thread.getState())))
                .toList();

        List<ThreadInfo> waiting = threads.stream()
                .filter(thread ->
                        "WAITING".equals(
                                normalizeState(thread.getState())))
                .toList();

        // Identify the top 10 threads by recorded CPU time.
        List<ThreadInfo> cpuConsumingThreads = threads.stream()
                .filter(thread -> thread.getCpuTimeMillis() != null)
                .sorted(Comparator.comparingDouble(
                        (ThreadInfo thread) ->
                                thread.getCpuTimeMillis()
                ).reversed())
                .limit(10)
                .toList();

        // Identify GC-related threads separately.
        // Threads with available CPU time appear first, in descending order.
        List<ThreadInfo> gcConsumingThreads = threads.stream()
                .filter(this::isGcThread)
                .sorted(Comparator.comparing(
                        ThreadInfo::getCpuTimeMillis,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(10)
                .toList();

        List<ThreadGroupSummary> groupSummaries =
                buildThreadGroupSummaries(threads);

        // Create unique internal thread identifiers.
        Map<String, String> threadNamesByKey = new HashMap<>();
        Map<String, String> lockOwners = new HashMap<>();
        Map<String, String> threadWaitingLocks = new HashMap<>();

        for (int i = 0; i < threads.size(); i++) {

            ThreadInfo thread = threads.get(i);
            String threadKey;

            if (thread.getId() > 0) {
                threadKey = "java:" + thread.getId();
            } else if (thread.getNativeThreadId() != null
                    && !thread.getNativeThreadId().isBlank()) {
                threadKey = "native:" + thread.getNativeThreadId();
            } else {
                threadKey = "thread:" + i;
            }

            threadNamesByKey.put(threadKey, thread.getName());

            for (String lock : thread.getOwnedLocks()) {
                lockOwners.put(lock, threadKey);
            }

            String waitingLock = thread.getWaitingForLock();

            if (waitingLock != null && !waitingLock.isBlank()) {
                threadWaitingLocks.put(threadKey, waitingLock);
            }
        }

        // Build the thread waiting graph.
        Map<String, String> waitingFor =
                deadlockDetector.buildWaitingGraph(
                        lockOwners,
                        threadWaitingLocks
                );

        // Detect deadlock cycles.
        List<List<String>> detectedCycles =
                deadlockDetector.findDeadlockCycles(waitingFor);

        boolean deadlockDetected = !detectedCycles.isEmpty();

        // Convert internal IDs to readable thread names.
        List<String> deadlockedThreads = detectedCycles.stream()
                .flatMap(List::stream)
                .map(threadNamesByKey::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<List<String>> readableDeadlockCycles =
                detectedCycles.stream()
                        .map(cycle -> cycle.stream()
                                .map(threadNamesByKey::get)
                                .filter(Objects::nonNull)
                                .toList())
                        .toList();

        ThreadAnalysisResult result = new ThreadAnalysisResult(
                total,
                daemon,
                nonDaemon,
                blocked,
                waiting,
                deadlockDetected,
                deadlockedThreads,
                threadStateCounts
        );

        result.setThreadGroupSummaries(groupSummaries);
        result.setAllThreads(threads);
        result.setDeadlockCycles(readableDeadlockCycles);

        // Include CPU and GC thread lists in the response.
        result.setCpuConsumingThreads(cpuConsumingThreads);
        result.setGcConsumingThreads(gcConsumingThreads);

        result.setUnknownDaemonThreads(unknownDaemon);

        // Include most frequently observed stack methods in the response.
        result.setMethodFrequency(methodFrequency);

        saveAnalysis(result);

        return result;
    }

    // Counts method appearances across captured stack traces.
    // This is a stack-snapshot frequency, not an actual execution count.
    private Map<String, Integer> calculateMethodFrequency(
            List<ThreadInfo> threads) {

        Map<String, Integer> methodFrequency = new HashMap<>();

        for (ThreadInfo thread : threads) {
            String stackTrace = thread.getStackTrace();

            if (stackTrace == null || stackTrace.isBlank()) {
                continue;
            }

            for (String line : stackTrace.split("\\R")) {
                String trimmedLine = line.trim();

                if (!trimmedLine.startsWith("at ")) {
                    continue;
                }

                String method = trimmedLine.substring(3);
                int parenthesisIndex = method.indexOf('(');

                if (parenthesisIndex > 0) {
                    method = method.substring(0, parenthesisIndex);
                }

                methodFrequency.merge(method, 1, Integer::sum);
            }
        }

        // Sort methods by frequency in descending order.
        Map<String, Integer> sortedFrequency = new LinkedHashMap<>();

        methodFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed())
                .forEach(entry ->
                        sortedFrequency.put(
                                entry.getKey(),
                                entry.getValue()
                        ));

        return sortedFrequency;
    }

    private boolean isGcThread(ThreadInfo thread) {

        String name = thread.getName();

        if (name == null || name.isBlank()) {
            return false;
        }

        String normalizedName = name.toLowerCase();

        return normalizedName.contains("gc")
                || normalizedName.contains("garbage collector")
                || normalizedName.startsWith("g1 ")
                || normalizedName.startsWith("g1-")
                || normalizedName.contains("shenandoah")
                || normalizedName.startsWith("zworker")
                || normalizedName.startsWith("zuncommit")
                || normalizedName.startsWith("zstat")
                || normalizedName.contains("concurrent mark")
                || normalizedName.contains("concurrent refinement");
    }

    private List<ThreadGroupSummary> buildThreadGroupSummaries(
            List<ThreadInfo> threads) {

        Map<String, ThreadGroupSummary> groups = new HashMap<>();

        for (ThreadInfo thread : threads) {

            String groupName = thread.getThreadGroupName();

            if (groupName == null || groupName.isBlank()) {
                groupName = "Unknown";
            }

            ThreadGroupSummary summary = groups.computeIfAbsent(
                    groupName,
                    ThreadGroupSummary::new
            );

            summary.setThreadCount(summary.getThreadCount() + 1);
            summary.getThreads().add(thread);

            String state = normalizeState(thread.getState());

            if ("BLOCKED".equals(state)) {
                summary.setBlockedCount(summary.getBlockedCount() + 1);
            } else if ("WAITING".equals(state)
                    || "TIMED_WAITING".equals(state)) {
                summary.setIdleCount(summary.getIdleCount() + 1);
            } else if ("RUNNABLE".equals(state)
                    || "NEW".equals(state)) {
                summary.setActiveCount(summary.getActiveCount() + 1);
            }
        }

        return groups.values().stream()
                .sorted(
                        Comparator.comparingInt(
                                ThreadGroupSummary::getThreadCount
                        ).reversed()
                )
                .toList();
    }

    private String normalizeState(String state) {
        return state == null
                ? "UNKNOWN"
                : state.trim().toUpperCase();
    }

    @Transactional
    public void saveAnalysis(ThreadAnalysisResult result) {

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

        entity.setDeadlockDetected(result.isDeadlockDetected());

        analysisResultRepository.save(entity);
    }

    public ThreadAnalysisResult analyzeFile(String filePath)
            throws IOException {

        ThreadDumpData dump = parser.parse(filePath);
        return analyze(dump);
    }
}