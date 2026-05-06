package com.vn.jmixcamel.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.dto.ErrorResponse;
import com.vn.jmixcamel.dto.ExecutionRequest;
import com.vn.jmixcamel.dto.ProbeRequest;
import com.vn.jmixcamel.runner.RestCallRunner;
import com.vn.jmixcamel.security.ConfigSecurityValidator;
import com.vn.jmixcamel.service.CamelDslEmitter;
import com.vn.jmixcamel.service.DynamicExecutionService;
import com.vn.jmixcamel.service.query.QueryableEntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dynamic")
public class DynamicExecutionController {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionController.class);

    private final DynamicExecutionService dynamicExecutionService;
    private final CamelDslEmitter camelDslEmitter;
    private final QueryableEntityRegistry queryableEntityRegistry;
    private final RestCallRunner restCallRunner;
    private final ConfigSecurityValidator configSecurityValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DynamicExecutionController(DynamicExecutionService dynamicExecutionService,
                                      CamelDslEmitter camelDslEmitter,
                                      QueryableEntityRegistry queryableEntityRegistry,
                                      RestCallRunner restCallRunner,
                                      ConfigSecurityValidator configSecurityValidator) {
        this.dynamicExecutionService = dynamicExecutionService;
        this.camelDslEmitter = camelDslEmitter;
        this.queryableEntityRegistry = queryableEntityRegistry;
        this.restCallRunner = restCallRunner;
        this.configSecurityValidator = configSecurityValidator;
    }

    @GetMapping("/entities")
    public ResponseEntity<List<String>> entities() {
        return ResponseEntity.ok(new ArrayList<>(queryableEntityRegistry.allEntities()));
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

    @PostMapping("/preview-yaml")
    public ResponseEntity<?> previewYaml(@RequestBody ExecutionRequest request) {
        if (request == null || request.getConfig() == null) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("INVALID_REQUEST", "config is required")
            );
        }
        String yaml = camelDslEmitter.toYaml(request.getConfig());
        return ResponseEntity.ok(Map.of("yaml", yaml));
    }

    /**
     * Probe a single REST_CALL — runs server-side (bypassing browser CORS) and returns the
     * parsed response so the designer UI can show field tree for auto-extract.
     */
    @PostMapping("/probe")
    public ResponseEntity<?> probe(@RequestBody ProbeRequest request) {
        if (request == null || request.getNodeData() == null) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("INVALID_REQUEST", "nodeData is required"));
        }
        configSecurityValidator.validateRestCallData("probe", request.getNodeData());

        Map<String, Object> scope = new HashMap<>();
        scope.put("input",     request.getInput()  == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getInput()));
        scope.put("object",    request.getObject() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getObject()));
        scope.put("output",    new LinkedHashMap<>());
        scope.put("extracted", new LinkedHashMap<>());
        scope.put("body",      null);
        scope.put("dbResult",  null);

        String raw = restCallRunner.run(request.getNodeData(), scope, null);
        Object parsed;
        boolean isJson;
        try {
            parsed = raw == null || raw.isBlank() ? null : objectMapper.readValue(raw, Object.class);
            isJson = parsed != null;
        } catch (Exception e) {
            parsed = raw;
            isJson = false;
        }
        return ResponseEntity.ok(Map.of(
                "body", parsed == null ? "" : parsed,
                "raw",  raw == null ? "" : raw,
                "isJson", isJson));
    }

    @PostMapping("/preview-xml")
    public ResponseEntity<?> previewXml(@RequestBody ExecutionRequest request) {
        if (request == null || request.getConfig() == null) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("INVALID_REQUEST", "config is required")
            );
        }
        String xml = camelDslEmitter.toXml(request.getConfig());
        return ResponseEntity.ok(Map.of("xml", xml));
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
