
package com.threadviewer.dto;

import java.util.ArrayList;
import java.util.List;

public class ThreadGroupSummary {

    private String groupName;
    private int threadCount;
    private int activeCount;
    private int idleCount;
    private int blockedCount;
    private List<ThreadInfo> threads = new ArrayList<>();

    public ThreadGroupSummary() {
    }

    public ThreadGroupSummary(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    public int getIdleCount() {
        return idleCount;
    }

    public void setIdleCount(int idleCount) {
        this.idleCount = idleCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public void setBlockedCount(int blockedCount) {
        this.blockedCount = blockedCount;
    }

    public List<ThreadInfo> getThreads() {
        return threads;
    }

    public void setThreads(List<ThreadInfo> threads) {
        this.threads = threads != null ? threads : new ArrayList<>();
    }
}
