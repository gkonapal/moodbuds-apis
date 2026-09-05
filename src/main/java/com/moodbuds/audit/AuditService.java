package com.moodbuds.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(long adminId, String action, String entityType, Object entityId, Object oldValue, Object newValue) {
        jdbc.sql("""
                INSERT INTO admin_activity_logs(admin_user_id, action, entity_type, entity_id, old_value, new_value, created_at)
                VALUES (:adminId, :action, :entityType, :entityId, CAST(:oldValue AS JSON), CAST(:newValue AS JSON), UTC_TIMESTAMP())
                """).param("adminId", adminId).param("action", action).param("entityType", entityType)
                .param("entityId", String.valueOf(entityId)).param("oldValue", json(oldValue)).param("newValue", json(newValue)).update();
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize audit value", exception);
        }
    }
}
