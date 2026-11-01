
package com.threadviewer.dto;

import java.util.ArrayList;
import java.util.List;

public class ThreadInfo {

    private String name;
    private long id;
    private int priority;
    private String state;
    private String stackTrace;
    private Boolean daemon;
    private String nativeThreadId;
    private Integer osPriority;
    private String waitingForLock;
    private List<String> ownedLocks = new ArrayList<>();
    private String threadGroupName = "Unknown";
    // new added
    private Double cpuTimeMillis;

    // Default constructor
    public ThreadInfo() {
    }

    // Backward-compatible constructor without daemon
    public ThreadInfo(
            String name,
            long id,
            int priority,
            String state,
            String stackTrace) {

        this.name = name;
        this.id = id;
        this.priority = priority;
        this.state = state;
        this.stackTrace = stackTrace;
    }

    // Backward-compatible constructor with daemon
    public ThreadInfo(
            String name,
            long id,
            int priority,
            String state,
            String stackTrace,
            boolean daemon) {

        this(name, id, priority, state, stackTrace);
        this.daemon = daemon;
    }

    // Getters
    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public String getState() {
        return state;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public Boolean isDaemon() {
        return daemon;
    }

    public String getNativeThreadId() {
        return nativeThreadId;
    }

    public Integer getOsPriority() {
        return osPriority;
    }

    public String getWaitingForLock() {
        return waitingForLock;
    }

    public List<String> getOwnedLocks() {
        return ownedLocks;
    }

    public String getThreadGroupName() {
        return threadGroupName;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void setDaemon(Boolean daemon) {
        this.daemon = daemon;
    }

    public void setNativeThreadId(String nativeThreadId) {
        this.nativeThreadId = nativeThreadId;
    }

    public void setOsPriority(Integer osPriority) {
        this.osPriority = osPriority;
    }

    public void setWaitingForLock(String waitingForLock) {
        this.waitingForLock = waitingForLock;
    }

    public void setOwnedLocks(List<String> ownedLocks) {
        this.ownedLocks = ownedLocks != null
                ? ownedLocks
                : new ArrayList<>();
    }

    public void setThreadGroupName(String threadGroupName) {
        this.threadGroupName =
                threadGroupName != null && !threadGroupName.isBlank()
                        ? threadGroupName
                        : "Unknown";
    }

    public Double getCpuTimeMillis() {
        return cpuTimeMillis;
    }

    public void setCpuTimeMillis(Double cpuTimeMillis) {
        this.cpuTimeMillis = cpuTimeMillis;
    }
}
