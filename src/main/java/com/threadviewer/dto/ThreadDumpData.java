package com.threadviewer.dto;

import java.util.List;

public class ThreadDumpData {

    private String timestamp;
    private String jvmInfo;
    private List<ThreadInfo> threads;

    public ThreadDumpData() {
    }

    public ThreadDumpData(String timestamp, String jvmInfo,
                          List<ThreadInfo> threads) {
        this.timestamp = timestamp;
        this.jvmInfo = jvmInfo;
        this.threads = threads;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getJvmInfo() {
        return jvmInfo;
    }

    public List<ThreadInfo> getThreads() {
        return threads;
    }
}