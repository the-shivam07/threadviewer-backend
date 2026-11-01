
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

    private static final Pattern NATIVE_THREAD_ID =
            Pattern.compile("\\bnid=([^\\s]+)");

    private static final Pattern OS_PRIORITY =
            Pattern.compile("\\bos_prio=(\\d+)");

    private static final Pattern LOCK_ID =
            Pattern.compile("<(0x[0-9a-fA-F]+)>");

    private static final Pattern DAEMON =
            Pattern.compile("\\bdaemon\\b");

    private static final Pattern THREAD_GROUP =
            Pattern.compile(
                    "\\bthreadGroup[=:]\\s*[\"']?([^\"'\\s,]+)"
            );

    private static final Pattern HEADER_STATE =
            Pattern.compile(
                    "\\b(timed_waiting|blocked|runnable|waiting)\\b"
            );

    private static final Pattern CPU_TIME =
            Pattern.compile("\\bcpu=([0-9]+(?:\\.[0-9]+)?)ms\\b");

    public ThreadDumpData parse(String filePath) throws IOException {

        List<String> lines = Files.readAllLines(Path.of(filePath));
        List<ThreadInfo> threads = new ArrayList<>();

        String timestamp = lines.isEmpty() ? "" : lines.get(0);
        String jvmInfo = "";

        String name = null;
        long id = 0;
        int priority = 0;
        String nativeThreadId = null;
        Integer osPriority = null;
        String state = "UNKNOWN";
        StringBuilder stackTrace = new StringBuilder();

        Boolean daemon = null;
        String waitingForLock = null;
        List<String> ownedLocks = new ArrayList<>();
        String threadGroupName = "Unknown";

        String currentHeaderLine = null;
        boolean threadStarted = false;

        for (String line : lines) {

            if (line.contains("Full thread dump")) {
                jvmInfo = line;
            }

            if (line.contains("JNI global references:")
                    || line.contains("Found one Java-level deadlock:")) {
                break;
            }

            Matcher headerMatcher = THREAD_HEADER.matcher(line);

            if (headerMatcher.matches()) {

                // Save the previous thread before reading the next one.
                if (threadStarted && name != null) {
                    threads.add(createThreadInfo(
                            name,
                            id,
                            priority,
                            nativeThreadId,
                            osPriority,
                            state,
                            stackTrace.toString(),
                            daemon,
                            waitingForLock,
                            ownedLocks,
                            threadGroupName,
                            currentHeaderLine
                    ));
                }

                // Read the new thread header.
                name = headerMatcher.group(1);
                String headerDetails = headerMatcher.group(2);
                currentHeaderLine = line;

                id = extractLong(THREAD_ID, headerDetails, 0);

                priority = (int) extractLong(
                        PRIORITY, headerDetails, 0
                );

                nativeThreadId = extractString(
                        NATIVE_THREAD_ID, headerDetails
                );

                osPriority = extractInteger(
                        OS_PRIORITY, headerDetails
                );

                /*
                 * A Java thread with a #number and no daemon marker
                 * is treated as non-daemon in this dump format.
                 *
                 * For internal threads without a Java thread number,
                 * daemon status is unknown unless explicitly marked.
                 */
                if (DAEMON.matcher(headerDetails).find()) {
                    daemon = true;
                } else if (THREAD_ID.matcher(headerDetails).find()) {
                    daemon = false;
                } else {
                    daemon = null;
                }

                threadGroupName = extractThreadGroup(name, headerDetails);

                // Some JVM internal threads expose state in the header.
                state = extractHeaderState(headerDetails);

                stackTrace = new StringBuilder();
                waitingForLock = null;
                ownedLocks = new ArrayList<>();

                threadStarted = true;
                continue;
            }

            if (!threadStarted) {
                continue;
            }

            // Prefer the explicit Java thread state when available.
            if (line.contains("java.lang.Thread.State:")) {
                String stateLine = line.substring(
                        line.indexOf("java.lang.Thread.State:")
                                + "java.lang.Thread.State:".length()
                ).trim();

                int bracketIndex = stateLine.indexOf('(');

                state = bracketIndex >= 0
                        ? stateLine.substring(0, bracketIndex).trim()
                        : stateLine;
            }

            Matcher lockMatcher = LOCK_ID.matcher(line);

            while (lockMatcher.find()) {
                String lockId = lockMatcher.group(1);

                if (line.contains("waiting to lock")) {
                    waitingForLock = lockId;
                }

                if (line.contains("locked <")) {
                    ownedLocks.add(lockId);
                }
            }

            stackTrace.append(line)
                    .append(System.lineSeparator());
        }

        // Save the final thread.
        if (threadStarted && name != null) {
            threads.add(createThreadInfo(
                    name,
                    id,
                    priority,
                    nativeThreadId,
                    osPriority,
                    state,
                    stackTrace.toString(),
                    daemon,
                    waitingForLock,
                    ownedLocks,
                    threadGroupName,
                    currentHeaderLine
            ));
        }

        return new ThreadDumpData(timestamp, jvmInfo, threads);
    }


    private ThreadInfo createThreadInfo(
            String name,
            long id,
            int priority,
            String nativeThreadId,
            Integer osPriority,
            String state,
            String stackTrace,
            Boolean daemon,
            String waitingForLock,
            List<String> ownedLocks,
            String threadGroupName,
            String headerLine) {

        // Temporary debug output for stack-trace investigation.
        System.out.println(
                "Thread: " + name
                        + " | Stack trace length: "
                        + (stackTrace == null ? 0 : stackTrace.length())
        );

        ThreadInfo threadInfo = new ThreadInfo(
                name,
                id,
                priority,
                state,
                stackTrace
        );

        // Extract CPU time from the original thread header.
        if (headerLine != null) {
            Matcher cpuMatcher = CPU_TIME.matcher(headerLine);

            if (cpuMatcher.find()) {
                try {
                    threadInfo.setCpuTimeMillis(
                            Double.parseDouble(cpuMatcher.group(1))
                    );
                } catch (NumberFormatException ignored) {
                    // Leave CPU time unavailable if parsing fails.
                }
            }
        }

        threadInfo.setDaemon(daemon);
        threadInfo.setNativeThreadId(nativeThreadId);
        threadInfo.setOsPriority(osPriority);
        threadInfo.setWaitingForLock(waitingForLock);
        threadInfo.setOwnedLocks(ownedLocks);
        threadInfo.setThreadGroupName(threadGroupName);

        return threadInfo;
    }

    private String extractHeaderState(String headerDetails) {

        Matcher matcher = HEADER_STATE.matcher(headerDetails);

        if (!matcher.find()) {
            return "UNKNOWN";
        }

        return matcher.group(1).toUpperCase();
    }



    private String extractThreadGroup(String threadName, String headerDetails) {
        Matcher matcher = THREAD_GROUP.matcher(headerDetails);

        // Use the actual thread group if the dump provides it.
        if (matcher.find()) {
            return matcher.group(1);
        }

        String name = threadName == null
                ? ""
                : threadName.toLowerCase();

        // Recognize common garbage-collector thread names.
        if (name.startsWith("gc ")
                || name.startsWith("gc task")
                || name.startsWith("g1 ")
                || name.startsWith("g1-")
                || name.contains("garbage collector")
                || name.startsWith("zworker")
                || name.startsWith("zuncommit")
                || name.startsWith("zstat")) {
            return "GC Threads";
        }

        // Recognize common JVM service threads.
        if (name.contains("compilerthread")
                || name.contains("service thread")
                || name.contains("sweeper")
                || name.contains("attach listener")
                || name.contains("signal dispatcher")
                || name.contains("reference handler")
                || name.contains("finalizer")
                || name.contains("vm thread")
                || name.contains("periodic task")
                || name.contains("destroyjavavm")) {
            return "JVM Internal Threads";
        }

        // Do not guess when the category cannot be identified.
        return "Unclassified";
    }


    private String extractString(Pattern pattern, String text) {

        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private Integer extractInteger(Pattern pattern, String text) {

        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private long extractLong(
            Pattern pattern,
            String text,
            long defaultValue) {

        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        return defaultValue;
    }
}
