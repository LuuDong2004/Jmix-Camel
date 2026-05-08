package com.vn.jmixcamel.transform.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.transform.runtime.PluginRegistry;
import com.vn.jmixcamel.transform.runtime.PluginUploadException;
import com.vn.jmixcamel.transform.runtime.PluginUploadService;
import com.vn.jmixcamel.transform.runtime.RegisteredExtension;
import com.vn.transform.spi.TransformDescriptor;
import com.vn.transform.spi.TransformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only catalog + admin runtime install/delete endpoints for transform plugins.
 *
 * <ul>
 *   <li>GET    /api/transforms/plugins                   — list visible plugins</li>
 *   <li>GET    /api/transforms/plugins/{id}/schema       — plugin's JSON Schema for config</li>
 *   <li>POST   /api/transforms/plugins/upload            — upload .jar (multipart, field "file")</li>
 *   <li>DELETE /api/transforms/plugins/{id}              — uninstall + delete JAR</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/transforms")
public class PluginCatalogController {

    private static final Logger log = LoggerFactory.getLogger(PluginCatalogController.class);

    private final PluginRegistry registry;
    private final PluginUploadService uploadService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PluginCatalogController(PluginRegistry registry, PluginUploadService uploadService) {
        this.registry = registry;
        this.uploadService = uploadService;
    }

    @GetMapping(value = "/plugins", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = registry.visiblePlugins().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping(value = "/extensions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> listExtensions() {
        List<Map<String, Object>> out = registry.listExtensions().stream()
                .map(this::toExtensionDto)
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping(value = "/plugins/{id}/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schema(@PathVariable String id) {
        return registry.resolve(id)
                .map(p -> {
                    String raw = p.configSchema();
                    if (raw == null || raw.isBlank()) {
                        return ResponseEntity.ok((Object) Map.of("type", "object"));
                    }
                    try {
                        JsonNode node = mapper.readTree(raw);
                        return ResponseEntity.ok((Object) node);
                    } catch (Exception e) {
                        log.warn("plugin {} schema is not valid JSON: {}", id, e.getMessage());
                        return ResponseEntity.internalServerError()
                                .body(Map.of("error", "schema_parse_failed", "message", e.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "plugin_not_found", "id", id)));
    }

    @PostMapping(value = "/plugins/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        TransformDescriptor d = uploadService.handleUpload(file, currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(d));
    }

    @DeleteMapping(value = "/plugins/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable String id) {
        uploadService.handleDelete(id, currentUser());
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ─── Error handlers ────────────────────────────────────────────────────────

    @ExceptionHandler(PluginUploadException.class)
    public ResponseEntity<?> handleUploadError(PluginUploadException ex) {
        log.warn("plugin upload failed: code={} message={}", ex.code(), ex.getMessage());
        HttpStatus status = switch (ex.code()) {
            case PLUGIN_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case PLUGIN_DISABLED -> HttpStatus.FORBIDDEN;
            case PLUGIN_LOAD_FAILED, IO_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of(
                "error", ex.code().name(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "FILE_TOO_LARGE",
                "message", ex.getMessage()));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(TransformPlugin p) {
        return toDto(p.descriptor());
    }

    private Map<String, Object> toDto(TransformDescriptor d) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", d.id());
        dto.put("version", d.version());
        dto.put("displayName", d.displayName());
        dto.put("description", d.description());
        dto.put("category", d.category());
        return dto;
    }

    private Map<String, Object> toExtensionDto(RegisteredExtension e) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("pluginCode", e.pluginCode());
        dto.put("extensionCode", e.extensionCode());
        dto.put("version", e.version());
        dto.put("displayName", e.displayName());
        dto.put("description", e.description());
        return dto;
    }

    private String currentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth == null ? "anonymous" : auth.getName();
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
