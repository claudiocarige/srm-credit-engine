package com.claudiocarige.backendcreditengine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deve verificar que as migrations V1 e V2 foram aplicadas com sucesso pelo Flyway")
    void shouldVerifyFlywayMigrationsAppliedSuccessfully() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).get("version")).isEqualTo("1");
        assertThat(rows.get(0).get("description")).isEqualTo("create assignors and receivables");
        assertThat(rows.get(0).get("success")).isEqualTo(true);

        assertThat(rows.get(1).get("version")).isEqualTo("2");
        assertThat(rows.get(1).get("description")).isEqualTo("create exchange rates");
        assertThat(rows.get(1).get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Deve validar os tipos, precisão, escala e nulabilidade das colunas no information_schema")
    void shouldVerifyColumnsMetadata() {
        assertColumn("assignors", "id", "uuid", null, null, null, "NO");
        assertColumn("assignors", "document", "character varying", 14, null, null, "NO");
        assertColumn("assignors", "name", "character varying", 150, null, null, "NO");
        assertColumn("assignors", "created_at", "timestamp with time zone", null, null, null, "NO");

        assertColumn("receivables", "id", "uuid", null, null, null, "NO");
        assertColumn("receivables", "assignor_id", "uuid", null, null, null, "NO");
        assertColumn("receivables", "document_number", "character varying", 64, null, null, "NO");
        assertColumn("receivables", "type", "character varying", 32, null, null, "NO");
        assertColumn("receivables", "face_value_brl", "numeric", null, 19, 2, "NO");
        assertColumn("receivables", "due_date", "date", null, null, null, "NO");
        assertColumn("receivables", "status", "character varying", 16, null, null, "NO");
        assertColumn("receivables", "version", "bigint", null, 64, 0, "NO");
        assertColumn("receivables", "created_at", "timestamp with time zone", null, null, null, "NO");

        assertColumn("exchange_rates", "id", "uuid", null, null, null, "NO");
        assertColumn("exchange_rates", "currency", "character varying", 3, null, null, "NO");
        assertColumn("exchange_rates", "rate_brl", "numeric", null, 19, 6, "NO");
        assertColumn("exchange_rates", "effective_at", "timestamp with time zone", null, null, null, "NO");
        assertColumn("exchange_rates", "created_at", "timestamp with time zone", null, null, null, "NO");
    }

    private void assertColumn(String tableName, String columnName, String expectedType,
                              Integer expectedCharLen, Integer expectedPrecision, Integer expectedScale,
                              String expectedNullable) {
        Map<String, Object> col = jdbcTemplate.queryForMap(
                """
                SELECT data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """,
                tableName, columnName
        );

        assertThat(col).as("Coluna %s.%s deve existir", tableName, columnName).isNotNull();
        assertThat(col.get("data_type")).as("Tipo de %s.%s", tableName, columnName).isEqualTo(expectedType);
        assertThat(col.get("is_nullable")).as("Nulabilidade de %s.%s", tableName, columnName).isEqualTo(expectedNullable);

        if (expectedCharLen != null) {
            assertThat(((Number) col.get("character_maximum_length")).intValue())
                    .as("Tamanho max de %s.%s", tableName, columnName).isEqualTo(expectedCharLen);
        }
        if (expectedPrecision != null) {
            assertThat(((Number) col.get("numeric_precision")).intValue())
                    .as("Precisão de %s.%s", tableName, columnName).isEqualTo(expectedPrecision);
        }
        if (expectedScale != null) {
            assertThat(((Number) col.get("numeric_scale")).intValue())
                    .as("Escala de %s.%s", tableName, columnName).isEqualTo(expectedScale);
        }
    }
}

