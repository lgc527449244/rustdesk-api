package com.rustdeskapi.server;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest(
        properties = "rustdesk.max-request-size=128")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestSizeLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/heartbeat", "/api/sysinfo", "/api/audit/conn"})
    void chunkedRequestLargerThanConfiguredLimitIsRejected(String path) throws Exception {
        byte[] body = ("""
                {
                  "id": "oversized-chunked",
                  "uuid": "oversized-chunked-uuid",
                  "padding": "%s"
                }
                """)
                .formatted("x".repeat(256))
                .getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(unknownLengthPost(path, body))
                .andExpect(status().is(413))
                .andExpect(content().string(""));
    }

    @Test
    void requestWithinConfiguredLimitIsReplayedToMvc() throws Exception {
        byte[] body = """
                {"id":"small-request","uuid":"small-request-uuid"}
                """.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(unknownLengthPost("/api/sysinfo", body))
                .andExpect(status().isOk())
                .andExpect(content().string("SYSINFO_UPDATED"));
    }

    @Test
    void sysinfoVersionIsNotSubjectToIngestionRequestLimit() throws Exception {
        byte[] body = ("{\"padding\":\"" + "x".repeat(256) + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(unknownLengthPost("/api/sysinfo_ver", body))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    private RequestBuilder unknownLengthPost(String path, byte[] body) {
        return servletContext -> {
            MockHttpServletRequest request = new UnknownLengthRequest(servletContext, path);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(body);
            return request;
        };
    }

    private static final class UnknownLengthRequest extends MockHttpServletRequest {

        private UnknownLengthRequest(ServletContext servletContext, String path) {
            super(servletContext, "POST", path);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
