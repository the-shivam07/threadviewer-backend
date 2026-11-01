package com.threadviewer.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeadlockDetector {

    public boolean hasDeadlock(Map<String, String> waitingFor) {

        return !findDeadlockCycles(waitingFor).isEmpty();
    }

    public Map<String, String> buildWaitingGraph(
            Map<String, String> lockOwners,
            Map<String, String> threadWaitingLocks) {

        Map<String, String> waitingFor = new HashMap<>();

        for (Map.Entry<String, String> entry
                : threadWaitingLocks.entrySet()) {

            String thread = entry.getKey();
            String waitingLock = entry.getValue();
            String owner = lockOwners.get(waitingLock);

            if (owner != null) {
                waitingFor.put(thread, owner);
            }
        }

        return waitingFor;
    }

    public List<List<String>> findDeadlockCycles(
            Map<String, String> waitingFor) {

        List<List<String>> cycles = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        for (String start : waitingFor.keySet()) {

            if (processed.contains(start)) {
                continue;
            }

            Map<String, Integer> visitedAt = new HashMap<>();
            List<String> path = new ArrayList<>();
            String current = start;

            while (current != null && waitingFor.containsKey(current)) {

                if (visitedAt.containsKey(current)) {
                    int cycleStart = visitedAt.get(current);

                    cycles.add(new ArrayList<>(
                            path.subList(cycleStart, path.size())
                    ));

                    break;
                }

                if (processed.contains(current)) {
                    break;
                }

                visitedAt.put(current, path.size());
                path.add(current);
                current = waitingFor.get(current);
            }

            processed.addAll(path);
        }

        return cycles;
    }
}
