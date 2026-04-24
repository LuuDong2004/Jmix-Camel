package com.vn.jmixcamel.rest;

import com.vn.jmixcamel.dto.ErrorResponse;
import com.vn.jmixcamel.dto.ExecutionRequest;
import com.vn.jmixcamel.service.DynamicExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dynamic")
public class DynamicExecutionController {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionController.class);

    private final DynamicExecutionService dynamicExecutionService;

    public DynamicExecutionController(DynamicExecutionService dynamicExecutionService) {
        this.dynamicExecutionService = dynamicExecutionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody ExecutionRequest request) {
        if (request == null || request.getConfig() == null) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("INVALID_REQUEST", "config is required")
            );
        }
        Object result = dynamicExecutionService.execute(request.getConfig());
        return ResponseEntity.ok(Map.of("result", result));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("EXECUTION_FAILED", ex.getMessage()));
    }
}
