package com.rustdeskapi.server.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeartbeatRequest(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 255) String uuid,
        @PositiveOrZero Long ver,
        List<Integer> conns,
        @JsonProperty("modified_at") Long modifiedAt) {
}
