
package com.threadviewer.repository;

import com.threadviewer.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisResultRepository
        extends JpaRepository<AnalysisResult, UUID> {
}
