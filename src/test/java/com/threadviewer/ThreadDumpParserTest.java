
package com.threadviewer;

import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadInfo;
import com.threadviewer.parser.ThreadDumpParser;

import java.io.IOException;

public class ThreadDumpParserTest {

    public static void main(String[] args) throws IOException {

        System.out.println("TEST: Latest test class is running");
        ThreadDumpParser parser = new ThreadDumpParser();

        String filePath = "sample-dumps/sample-thread-dump.txt";

        ThreadDumpData dump = parser.parse(filePath);

        System.out.println("Total Threads: " + dump.getThreads().size());

        for (ThreadInfo thread : dump.getThreads()) {

            System.out.println("--------------------");
            System.out.println("Name: " + thread.getName());
            System.out.println("ID: " + thread.getId());
            System.out.println("State: " + thread.getState());
            System.out.println("Waiting Lock: " + thread.getWaitingForLock());
            System.out.println("Owned Locks: " + thread.getOwnedLocks());
        }
    }
}
