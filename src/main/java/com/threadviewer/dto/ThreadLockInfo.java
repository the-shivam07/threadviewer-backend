
package com.threadviewer.dto;

import java.util.List;

public class ThreadLockInfo {

    private String threadName;
    private long threadId;
    private String waitingForLock;
    private List<String> ownedLocks;

    public ThreadLockInfo() {
    }

    public ThreadLockInfo(
            String threadName,
            long threadId,
            String waitingForLock,
            List<String> ownedLocks) {

        this.threadName = threadName;
        this.threadId = threadId;
        this.waitingForLock = waitingForLock;
        this.ownedLocks = ownedLocks;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getWaitingForLock() {
        return waitingForLock;
    }

    public List<String> getOwnedLocks() {
        return ownedLocks;
    }
}
