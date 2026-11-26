package com.menufood.userservice.dao.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

@Repository
public class AccountStateRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String table;

    public AccountStateRepository(JdbcTemplate jdbc, ObjectMapper json, @Value("${spring.flyway.default-schema:public}") String schema) {
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Invalid workspace schema");
        this.jdbc = jdbc;
        this.json = json;
        this.table = "\"" + schema + "\".workspace_accounts";
    }

    public record Account(UUID id, long serviceId, ObjectNode state) {}

    public Account load(UUID id) {
        jdbc.update("INSERT INTO " + table + "(account_id) VALUES (?) ON CONFLICT(account_id) DO NOTHING", id);
        return jdbc.queryForObject("SELECT service_user_id, state FROM " + table + " WHERE account_id=? FOR UPDATE",
                (row, index) -> new Account(id, row.getLong(1), (ObjectNode) json.readTree(row.getString(2))), id);
    }

    public void save(Account account) {
        jdbc.update("UPDATE " + table + " SET state=? WHERE account_id=?", account.state().toString(), account.id());
    }
}
