
package com.threadviewer.parser;

import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.dto.ThreadInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThreadDumpParser {

    private static final Pattern THREAD_HEADER =
            Pattern.compile("^\"([^\"]+)\"(.*)$");

    private static final Pattern THREAD_ID =
            Pattern.compile("\\s+#(\\d+)\\b");

    private static final Pattern PRIORITY =
            Pattern.compile("\\bprio=(\\d+)");

    private static final Pattern LOCK_ID =
            Pattern.compile("<(0x[0-9a-fA-F]+)>");

    private static final Pattern DAEMON =
            Pattern.compile("\\bdaemon\\b");

    public ThreadDumpData parse(String filePath) throws IOException {

        List<String> lines = Files.readAllLines(Path.of(filePath));
        List<ThreadInfo> threads = new ArrayList<>();

        String timestamp = lines.isEmpty() ? "" : lines.get(0).trim();
        String jvmInfo = "";

        String name = "";
        long id = 0;
        int priority = 0;
        String state = "UNKNOWN";

        StringBuilder stackTrace = new StringBuilder();

        boolean daemon = false;
        String waitingForLock = null;
        List<String> ownedLocks = new ArrayList<>();

        boolean threadStarted = false;

        for (String line : lines) {

            if (line.startsWith("Full thread dump")) {
                jvmInfo = line.trim();
                continue;
            }

            // Stop before the deadlock summary section.
            if (line.startsWith("JNI global references:")
                    || line.startsWith("Found one Java-level deadlock:")) {
                break;
            }

            Matcher headerMatcher = THREAD_HEADER.matcher(line);

            if (headerMatcher.matches()) {

                // Save the previous thread.
                if (threadStarted) {
                    ThreadInfo thread = new ThreadInfo(
                            name, id, priority, state,
                            stackTrace.toString().trim(), daemon
                    );

                    thread.setWaitingForLock(waitingForLock);
                    thread.setOwnedLocks(new ArrayList<>(ownedLocks));
                    threads.add(thread);
                }

                // Read the thread name and header details.
                name = headerMatcher.group(1);
                String headerDetails = headerMatcher.group(2);

                // Some JVM threads do not have a numeric Java thread ID.
                Matcher idMatcher = THREAD_ID.matcher(headerDetails);
                id = idMatcher.find()
                        ? Long.parseLong(idMatcher.group(1))
                        : 0L;

                Matcher priorityMatcher = PRIORITY.matcher(headerDetails);
                priority = priorityMatcher.find()
                        ? Integer.parseInt(priorityMatcher.group(1))
                        : 0;

                daemon = DAEMON.matcher(headerDetails).find();

                // Reset values for the new thread.
                state = "UNKNOWN";
                stackTrace = new StringBuilder();
                waitingForLock = null;
                ownedLocks = new ArrayList<>();

                threadStarted = true;
                continue;
            }

            if (!threadStarted) {
                continue;
            }

            // Normalize states such as BLOCKED (on object monitor).
            if (line.contains("java.lang.Thread.State:")) {

                String stateDetails = line.substring(
                        line.indexOf("java.lang.Thread.State:")
                                + "java.lang.Thread.State:".length()
                ).trim();

                state = stateDetails.split("[\\s(]", 2)[0];

                stackTrace.append(line).append("\n");
                continue;
            }

            Matcher lockMatcher = LOCK_ID.matcher(line);

            if (line.contains("waiting to lock")) {

                if (lockMatcher.find()) {
                    waitingForLock = lockMatcher.group(1);
                }

            } else if (line.contains("locked <")) {

                if (lockMatcher.find()) {
                    ownedLocks.add(lockMatcher.group(1));
                }
            }

            stackTrace.append(line).append("\n");
        }

        // Save the final thread.
        if (threadStarted) {
            ThreadInfo thread = new ThreadInfo(
                    name, id, priority, state,
                    stackTrace.toString().trim(), daemon
            );

            thread.setWaitingForLock(waitingForLock);
            thread.setOwnedLocks(new ArrayList<>(ownedLocks));
            threads.add(thread);
        }

        return new ThreadDumpData(timestamp, jvmInfo, threads);
    }
}
