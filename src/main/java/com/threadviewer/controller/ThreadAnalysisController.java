
package com.threadviewer.controller;

import com.threadviewer.dto.ThreadAnalysisResult;
import com.threadviewer.dto.ThreadDumpData;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.service.ThreadAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/thread")
public class ThreadAnalysisController {

    private final ThreadAnalysisService analysisService;
    private final ThreadDumpParser parser;

    public ThreadAnalysisController(
            ThreadAnalysisService analysisService,
            ThreadDumpParser parser) {
        this.analysisService = analysisService;
        this.parser = parser;
    }

    @PostMapping("/analyze")
    public ThreadAnalysisResult analyze(
            @RequestBody ThreadDumpData dump) {
        return analysisService.analyze(dump);
    }

    @PostMapping("/upload")
    public ThreadAnalysisResult uploadAndAnalyze(
            @RequestParam("file") MultipartFile file) {

        // 1. Check empty file
        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please upload a non-empty thread dump file"
            );
        }

        // 2. Validate file extension
        String originalFilename = file.getOriginalFilename();

        String filename = StringUtils.cleanPath(
                originalFilename == null ? "" : originalFilename
        );

        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT)
                : "";

        if (!List.of("txt", "tdump", "log").contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Allowed: txt, tdump, log"
            );
        }

        // 3. Check file size: maximum 10 MB
        long maxFileSize = 10L * 1024 * 1024;

        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "File size must not exceed 10 MB"
            );
        }

        Path tempFile = null;

        try {
            // 4. Create temporary file
            tempFile = Files.createTempFile(
                    "threadviewer-", ".txt"
            );

            // 5. Copy uploaded content to temporary file
            file.transferTo(tempFile);

            // 6. Parse the thread dump
            ThreadDumpData dump =
                    parser.parse(tempFile.toString());

            // 7. Analyze parsed data
            return analysisService.analyze(dump);

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process thread dump file",
                    e
            );

        } finally {
            // 8. Attempt temporary file cleanup
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Production code should log cleanup failures.
                }
            }
        }
    }
}
