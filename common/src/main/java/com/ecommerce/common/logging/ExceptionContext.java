package com.ecommerce.common.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String exceptionType;
    private String message;
    private List<String> stackTrace;
    private String rootCause;
    private String correlationId;
    private String requestId;
    private String userId;
    private LocalDateTime timestamp;
    private long duration;
    private Map<String, Object> contextData;

    public static ExceptionContext from(Throwable throwable, String correlationId, String requestId) {
        List<String> trace = extractStackTrace(throwable);
        String cause = findRootCause(throwable);

        return ExceptionContext.builder()
            .exceptionType(throwable.getClass().getName())
            .message(throwable.getMessage())
            .correlationId(correlationId)
            .requestId(requestId)
            .timestamp(LocalDateTime.now())
            .stackTrace(trace)
            .rootCause(cause)
            .contextData(new HashMap<>())
            .build();
    }

    private static List<String> extractStackTrace(Throwable throwable) {
        List<String> trace = new ArrayList<>();
        StackTraceElement[] elements = throwable.getStackTrace();
        int limit = Math.min(20, elements.length);

        for (int i = 0; i < limit; i++) {
            trace.add(elements[i].toString());
        }

        if (elements.length > limit) {
            trace.add("... " + (elements.length - limit) + " more");
        }

        return trace;
    }

    private static String findRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    public void addContextData(String key, Object value) {
        if (contextData == null) {
            contextData = new HashMap<>();
        }
        contextData.put(key, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("exceptionType", exceptionType);
        map.put("message", message);
        map.put("stackTrace", stackTrace);
        map.put("rootCause", rootCause);
        map.put("correlationId", correlationId);
        map.put("requestId", requestId);
        map.put("userId", userId);
        map.put("timestamp", timestamp);
        map.put("duration", duration);
        map.put("contextData", contextData);
        return map;
    }
}
