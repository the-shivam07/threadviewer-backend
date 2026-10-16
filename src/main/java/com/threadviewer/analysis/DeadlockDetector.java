
package com.threadviewer.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeadlockDetector {

    public boolean hasDeadlock(Map<String, String> waitingFor) {

        for (String thread : waitingFor.keySet()) {

            Set<String> visited = new HashSet<>();
            String current = thread;

            while (current != null) {

                if (!visited.add(current)) {
                    return true;
                }

                current = waitingFor.get(current);
            }
        }

        return false;
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
}
