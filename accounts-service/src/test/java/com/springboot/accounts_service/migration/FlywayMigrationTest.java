package com.springboot.accounts_service.migration;

import com.springboot.accounts_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Estes testes não usam a entidade nem o repository: perguntam direto ao MySQL
// como a tabela ficou depois de TODAS as migrations (V1, V2, V3...).
// O MySQL descreve as próprias tabelas no schema INFORMATION_SCHEMA,
// que pode ser consultado com SELECT como qualquer outra tabela.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void accountsTableIsCreatedByFlyway() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    void idShouldBeBigintAutoIncrement() {
        // INFORMATION_SCHEMA.COLUMNS tem uma linha por coluna de cada tabela.
        // TABLE_SCHEMA = DATABASE() limita ao banco atual: no Testcontainers ele se chama "test",
        // não "digital_account", e o MySQL tem outros schemas com tabelas próprias.
        String columnType = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'id'
                """, String.class);

        // A coluna EXTRA guarda propriedades especiais, como "auto_increment".
        String extra = jdbcTemplate.queryForObject("""
                SELECT EXTRA FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'id'
                """, String.class);

        assertThat(columnType).isEqualTo("bigint");
        assertThat(extra).contains("auto_increment");
    }

    @Test
    void documentShouldHaveUniqueIndex() {
        // INFORMATION_SCHEMA.STATISTICS tem uma linha por coluna de cada índice.
        String indexedColumn = jdbcTemplate.queryForObject("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND INDEX_NAME = 'uk_document'
                """, String.class);

        // NON_UNIQUE = 0 significa que o índice NÃO aceita valores repetidos, ou seja, é único.
        Integer nonUnique = jdbcTemplate.queryForObject("""
                SELECT NON_UNIQUE FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND INDEX_NAME = 'uk_document'
                """, Integer.class);

        assertThat(indexedColumn).isEqualTo("document");
        assertThat(nonUnique).isZero();
    }

    @Test
    void allColumnsShouldBeNotNull() {
        // Busca todas as colunas que NÃO aceitam nulo (IS_NULLABLE = 'NO').
        List<String> notNullColumns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND IS_NULLABLE = 'NO'
                """, String.class);

        // containsExactlyInAnyOrder falha se alguma dessas colunas passar a aceitar nulo
        // ou se surgir outra coluna NOT NULL que não esteja na lista.
        assertThat(notNullColumns)
                .containsExactlyInAnyOrder("id", "owner_name", "document", "status", "created_at");
    }
}
