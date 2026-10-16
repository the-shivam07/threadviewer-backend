
package com.threadviewer.dto;

import java.util.ArrayList;
import java.util.List;

public class ThreadInfo {

    private String name;
    private long id;
    private int priority;
    private String state;
    private String stackTrace;
    private boolean daemon;

    private String waitingForLock;
    private List<String> ownedLocks = new ArrayList<>();

    public ThreadInfo() {
    }

    public ThreadInfo(String name, long id, int priority,
                      String state, String stackTrace) {
        this.name = name;
        this.id = id;
        this.priority = priority;
        this.state = state;
        this.stackTrace = stackTrace;
    }

    public ThreadInfo(String name, long id, int priority,
                      String state, String stackTrace,
                      boolean daemon) {
        this.name = name;
        this.id = id;
        this.priority = priority;
        this.state = state;
        this.stackTrace = stackTrace;
        this.daemon = daemon;
    }

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

    public boolean isDaemon() {
        return daemon;
    }

    public String getWaitingForLock() {
        return waitingForLock;
    }

    public void setWaitingForLock(String waitingForLock) {
        this.waitingForLock = waitingForLock;
    }

    public List<String> getOwnedLocks() {
        return ownedLocks;
    }

    public void setOwnedLocks(List<String> ownedLocks) {
        this.ownedLocks = ownedLocks;
    }
}
