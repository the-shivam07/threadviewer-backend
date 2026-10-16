
package com.threadviewer;

import com.threadviewer.controller.ThreadAnalysisController;
import com.threadviewer.parser.ThreadDumpParser;
import com.threadviewer.service.ThreadAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ThreadAnalysisControllerTest {

    private final ThreadDumpParser parser = new ThreadDumpParser();

    private final ThreadAnalysisService service =
            new ThreadAnalysisService(parser);

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(
                    new ThreadAnalysisController(service, parser)
            ).build();

    @Test
    void shouldRejectEmptyFile() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/thread/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUnsupportedFileType() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "sample content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/thread/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFileLargerThan10MB() throws Exception {

        byte[] largeContent = new byte[10 * 1024 * 1024 + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                MediaType.TEXT_PLAIN_VALUE,
                largeContent
        );

        mockMvc.perform(multipart("/api/v1/thread/upload")
                        .file(file))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void shouldAcceptValidThreadDumpFile() throws Exception {

        byte[] content = Files.readAllBytes(
                Path.of("sample-dumps", "sample-thread-dump.txt")
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-thread-dump.txt",
                MediaType.TEXT_PLAIN_VALUE,
                content
        );

        mockMvc.perform(multipart("/api/v1/thread/upload")
                        .file(file))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHandleInvalidThreadDumpContent() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "this is not a valid thread dump".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/thread/upload")
                        .file(file))
                .andExpect(status().isOk());
    }
}
