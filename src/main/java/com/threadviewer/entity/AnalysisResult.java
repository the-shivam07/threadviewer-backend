
package com.threadviewer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDateTime createdAt;

    private int totalThreads;

    private int daemonThreads;

    private int nonDaemonThreads;

    private int blockedThreadCount;

    private int waitingThreadCount;

    private boolean deadlockDetected;

    public AnalysisResult() {
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public void setTotalThreads(int totalThreads) {
        this.totalThreads = totalThreads;
    }

    public int getDaemonThreads() {
        return daemonThreads;
    }

    public void setDaemonThreads(int daemonThreads) {
        this.daemonThreads = daemonThreads;
    }

    public int getNonDaemonThreads() {
        return nonDaemonThreads;
    }

    public void setNonDaemonThreads(int nonDaemonThreads) {
        this.nonDaemonThreads = nonDaemonThreads;
    }

    public int getBlockedThreadCount() {
        return blockedThreadCount;
    }

    public void setBlockedThreadCount(int blockedThreadCount) {
        this.blockedThreadCount = blockedThreadCount;
    }

    public int getWaitingThreadCount() {
        return waitingThreadCount;
    }

    public void setWaitingThreadCount(int waitingThreadCount) {
        this.waitingThreadCount = waitingThreadCount;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public void setDeadlockDetected(boolean deadlockDetected) {
        this.deadlockDetected = deadlockDetected;
    }
}
