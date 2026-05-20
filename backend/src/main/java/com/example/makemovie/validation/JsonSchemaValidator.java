package com.example.makemovie.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class JsonSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    public Set<ValidationMessage> validate(String schemaJson, String dataJson) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode dataNode = objectMapper.readTree(dataJson);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            return schema.validate(dataNode);
        } catch (Exception e) {
            log.error("Schema validation failed: {}", e.getMessage());
            return Set.of(ValidationMessage.builder().message("Schema parse error: " + e.getMessage()).build());
        }
    }
}
