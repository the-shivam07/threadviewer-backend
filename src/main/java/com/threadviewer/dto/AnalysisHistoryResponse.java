
package com.threadviewer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class AnalysisHistoryResponse {

    private UUID id;
    private LocalDateTime createdAt;
    private int totalThreads;
    private int daemonThreads;
    private int nonDaemonThreads;
    private int blockedThreadCount;
    private int waitingThreadCount;
    private boolean deadlockDetected;

    public AnalysisHistoryResponse() {
    }

    public AnalysisHistoryResponse(
            UUID id,
            LocalDateTime createdAt,
            int totalThreads,
            int daemonThreads,
            int nonDaemonThreads,
            int blockedThreadCount,
            int waitingThreadCount,
            boolean deadlockDetected) {

        this.id = id;
        this.createdAt = createdAt;
        this.totalThreads = totalThreads;
        this.daemonThreads = daemonThreads;
        this.nonDaemonThreads = nonDaemonThreads;
        this.blockedThreadCount = blockedThreadCount;
        this.waitingThreadCount = waitingThreadCount;
        this.deadlockDetected = deadlockDetected;
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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

    public int getBlockedThreadCount() {
        return blockedThreadCount;
    }

    public int getWaitingThreadCount() {
        return waitingThreadCount;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }
}
