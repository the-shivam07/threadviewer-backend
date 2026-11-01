package com.threadviewer.controller;

import com.threadviewer.dto.AnalysisHistoryResponse;
import com.threadviewer.service.ThreadHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/thread")
public class ThreadHistoryController {

    private final ThreadHistoryService historyService;

    public ThreadHistoryController(ThreadHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public List<AnalysisHistoryResponse> getHistory() {
        return historyService.getHistory();
    }
}
