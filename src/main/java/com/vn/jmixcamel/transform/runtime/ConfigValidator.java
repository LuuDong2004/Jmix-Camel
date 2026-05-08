package com.vn.jmixcamel.transform.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.vn.transform.spi.TransformException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JSON Schema validation for plugin config. Schemas are cached by plugin id+version
 * to avoid re-parsing on every execution.
 */
@Component
public class ConfigValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * @throws TransformException with code {@code BAD_CONFIG} if the config does not match
     *         the supplied JSON Schema.
     */
    public void validate(String cacheKey, String schemaJson, Map<String, Object> config) {
        if (schemaJson == null || schemaJson.isBlank()) return; // plugin opted out
        JsonSchema schema = schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                return factory.getSchema(mapper.readTree(schemaJson));
            } catch (Exception e) {
                throw new TransformException(TransformException.Code.BAD_CONFIG,
                        "plugin schema is not valid JSON: " + e.getMessage(), e);
            }
        });

        try {
            JsonNode configNode = mapper.valueToTree(config == null ? new LinkedHashMap<>() : config);
            Set<ValidationMessage> errors = schema.validate(configNode);
            if (!errors.isEmpty()) {
                String summary = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new TransformException(TransformException.Code.BAD_CONFIG,
                        "config violates plugin schema: " + summary);
            }
        } catch (TransformException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformException(TransformException.Code.BAD_CONFIG,
                    "config validation failed: " + e.getMessage(), e);
        }
    }
}
