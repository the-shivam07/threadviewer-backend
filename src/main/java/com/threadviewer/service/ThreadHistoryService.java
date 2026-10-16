
package com.threadviewer.service;

import com.threadviewer.dto.AnalysisHistoryResponse;
import com.threadviewer.entity.AnalysisResult;
import com.threadviewer.repository.AnalysisResultRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ThreadHistoryService {

    private final AnalysisResultRepository repository;

    public ThreadHistoryService(AnalysisResultRepository repository) {
        this.repository = repository;
    }

    public List<AnalysisHistoryResponse> getHistory() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AnalysisHistoryResponse mapToResponse(AnalysisResult entity) {
        return new AnalysisHistoryResponse(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getTotalThreads(),
                entity.getDaemonThreads(),
                entity.getNonDaemonThreads(),
                entity.getBlockedThreadCount(),
                entity.getWaitingThreadCount(),
                entity.isDeadlockDetected()
        );
    }
}
