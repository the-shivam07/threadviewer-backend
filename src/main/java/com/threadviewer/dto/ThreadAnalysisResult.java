package com.threadviewer.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThreadAnalysisResult {

    private int totalThreads;
    private int daemonThreads;
    private int nonDaemonThreads;
    private int unknownDaemonThreads;

    private List<ThreadInfo> blockedThreads;
    private List<ThreadInfo> waitingThreads;

    private Map<String, Integer> threadStateCounts;

    private boolean deadlockDetected;
    private List<String> deadlockedThreads;
    private List<List<String>> deadlockCycles = new ArrayList<>();

    private List<ThreadGroupSummary> threadGroupSummaries;
    private List<ThreadInfo> allThreads;

    // CPU-consuming threads
    private List<ThreadInfo> cpuConsumingThreads = new ArrayList<>();

    // GC-related threads
    private List<ThreadInfo> gcConsumingThreads = new ArrayList<>();

    // Most frequently observed methods
    private Map<String, Integer> methodFrequency = new LinkedHashMap<>();

    public ThreadAnalysisResult() {
    }

    public ThreadAnalysisResult(
            int totalThreads,
            int daemonThreads,
            int nonDaemonThreads,
            List<ThreadInfo> blockedThreads,
            List<ThreadInfo> waitingThreads) {

        this.totalThreads = totalThreads;
        this.daemonThreads = daemonThreads;
        this.nonDaemonThreads = nonDaemonThreads;
        this.blockedThreads = blockedThreads;
        this.waitingThreads = waitingThreads;
        this.deadlockDetected = false;
    }

    public ThreadAnalysisResult(
            int totalThreads,
            int daemonThreads,
            int nonDaemonThreads,
            List<ThreadInfo> blockedThreads,
            List<ThreadInfo> waitingThreads,
            boolean deadlockDetected,
            List<String> deadlockedThreads,
            Map<String, Integer> threadStateCounts) {

        this.totalThreads = totalThreads;
        this.daemonThreads = daemonThreads;
        this.nonDaemonThreads = nonDaemonThreads;
        this.blockedThreads = blockedThreads;
        this.waitingThreads = waitingThreads;
        this.deadlockDetected = deadlockDetected;
        this.deadlockedThreads = deadlockedThreads;
        this.threadStateCounts = threadStateCounts;
    }

    public int getTotalThreads() {
        return totalThreads;
    }

    public int getDaemonThreads() {
        return daemonThreads;
    }

    public int getNonDaemonThreads() {
        return nonDaemonThreads;
    }

    public List<ThreadInfo> getBlockedThreads() {
        return blockedThreads;
    }

    public List<ThreadInfo> getWaitingThreads() {
        return waitingThreads;
    }

    public Map<String, Integer> getThreadStateCounts() {
        return threadStateCounts;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<String> getDeadlockedThreads() {
        return deadlockedThreads;
    }

    public List<List<String>> getDeadlockCycles() {
        return deadlockCycles;
    }

    public void setDeadlockCycles(List<List<String>> deadlockCycles) {
        this.deadlockCycles = deadlockCycles != null
                ? deadlockCycles
                : new ArrayList<>();
    }

    public List<ThreadGroupSummary> getThreadGroupSummaries() {
        return threadGroupSummaries;
    }

    public void setThreadGroupSummaries(
            List<ThreadGroupSummary> threadGroupSummaries) {
        this.threadGroupSummaries = threadGroupSummaries;
    }

    public List<ThreadInfo> getAllThreads() {
        return allThreads;
    }

    public void setAllThreads(List<ThreadInfo> allThreads) {
        this.allThreads = allThreads;
    }

    // CPU-consuming threads getter and setter
    public List<ThreadInfo> getCpuConsumingThreads() {
        return cpuConsumingThreads;
    }

    public void setCpuConsumingThreads(
            List<ThreadInfo> cpuConsumingThreads) {
        this.cpuConsumingThreads = cpuConsumingThreads != null
                ? cpuConsumingThreads
                : new ArrayList<>();
    }

    // GC-related threads getter and setter
    public List<ThreadInfo> getGcConsumingThreads() {
        return gcConsumingThreads;
    }

    public void setGcConsumingThreads(
            List<ThreadInfo> gcConsumingThreads) {
        this.gcConsumingThreads = gcConsumingThreads != null
                ? gcConsumingThreads
                : new ArrayList<>();
    }

    public int getUnknownDaemonThreads() {
        return unknownDaemonThreads;
    }

    public void setUnknownDaemonThreads(int unknownDaemonThreads) {
        this.unknownDaemonThreads = unknownDaemonThreads;
    }

    // Most frequently observed methods getter and setter
    public Map<String, Integer> getMethodFrequency() {
        return methodFrequency;
    }

    public void setMethodFrequency(
            Map<String, Integer> methodFrequency) {
        this.methodFrequency = methodFrequency != null
                ? new LinkedHashMap<>(methodFrequency)
                : new LinkedHashMap<>();
    }
}