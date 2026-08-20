package com.rustdeskapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.rustdeskapi.server.device.DeviceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceConcurrencyIntegrationTest {

    private static final int REQUEST_COUNT = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceRepository deviceRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAllInBatch();
        executor = Executors.newFixedThreadPool(REQUEST_COUNT);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentHeartbeatsForANewDeviceAllSucceedAndCreateOneRow() throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();

        for (int request = 0; request < REQUEST_COUNT; request++) {
            responses.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/heartbeat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "id": "concurrent-device",
                                          "uuid": "concurrent-uuid",
                                          "ver": 1409000,
                                          "conns": [1]
                                        }
                                        """))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        for (Future<Integer> response : responses) {
            assertThat(response.get(10, TimeUnit.SECONDS)).isBetween(200, 299);
        }
        assertThat(deviceRepository.findAll())
                .singleElement()
                .extracting(device -> device.getRustdeskId())
                .isEqualTo("concurrent-device");
    }
}
