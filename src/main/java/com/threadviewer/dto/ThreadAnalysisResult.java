
package com.threadviewer.dto;

import java.util.List;

public class ThreadAnalysisResult {

    private int totalThreads;
    private int daemonThreads;
    private int nonDaemonThreads;
    private List<ThreadInfo> blockedThreads;
    private List<ThreadInfo> waitingThreads;

    // New fields for deadlock detection
    private boolean deadlockDetected;
    private List<String> deadlockedThreads;

    public ThreadAnalysisResult() {
    }

    // Existing constructor - preserved for compatibility
    public ThreadAnalysisResult(int totalThreads,
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

    // New constructor
    public ThreadAnalysisResult(int totalThreads,
                                int daemonThreads,
                                int nonDaemonThreads,
                                List<ThreadInfo> blockedThreads,
                                List<ThreadInfo> waitingThreads,
                                boolean deadlockDetected,
                                List<String> deadlockedThreads) {
        this.totalThreads = totalThreads;
        this.daemonThreads = daemonThreads;
        this.nonDaemonThreads = nonDaemonThreads;
        this.blockedThreads = blockedThreads;
        this.waitingThreads = waitingThreads;
        this.deadlockDetected = deadlockDetected;
        this.deadlockedThreads = deadlockedThreads;
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

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<String> getDeadlockedThreads() {
        return deadlockedThreads;
    }
}
