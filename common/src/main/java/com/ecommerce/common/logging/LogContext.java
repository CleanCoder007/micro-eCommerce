package com.ecommerce.common.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String correlationId;
    private String requestId;
    private String userId;
    private String customerId;
    private String serviceName;
    private String componentName;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public LogContext(String correlationId, String requestId, String serviceName) {
        this.correlationId = correlationId;
        this.requestId = requestId;
        this.serviceName = serviceName;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("correlationId", correlationId);
        map.put("requestId", requestId);
        map.put("userId", userId);
        map.put("customerId", customerId);
        map.put("serviceName", serviceName);
        map.put("componentName", componentName);
        map.put("timestamp", timestamp);
        map.put("metadata", metadata);
        return map;
    }
}
