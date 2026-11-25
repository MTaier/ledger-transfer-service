package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mostra quais travas o findCustomerByIdForUpdate pega no InnoDB.
 * A trava e pega dentro de uma transacao aberta, e uma segunda conexao
 * le a tabela performance_schema.data_locks enquanto ela ainda esta ativa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountLockTest {

    // Uma linha de performance_schema.data_locks.
    record RowLock(String indexName, String lockMode, String lockData) {
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MySQLContainer mysql;

    @AfterEach
    void cleanUp() {
        accountRepository.deleteByAccountType(AccountType.CUSTOMER);
    }

    @Test
    @DisplayName("Locking an existing account takes a single record lock on the primary key, with no gap lock")
    void shouldLockOnlyTheAccountRowWhenItExists() {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));

        var locks = transactionTemplate.execute(status -> {
            accountRepository.findCustomerByIdForUpdate(account.getId());
            return readAccountRowLocks();
        });

        // X,REC_NOT_GAP: trava exclusiva so no registro, sem o intervalo ao redor.
        assertThat(locks).containsExactly(
                new RowLock("PRIMARY", "X,REC_NOT_GAP", String.valueOf(account.getId())));
    }

    @Test
    @DisplayName("Locking a missing account takes a gap lock instead of a record lock")
    void shouldTakeAGapLockWhenTheAccountDoesNotExist() {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var missingId = account.getId() + 1000;

        var locks = transactionTemplate.execute(status -> {
            accountRepository.findCustomerByIdForUpdate(missingId);
            return readAccountRowLocks();
        });

        // Sem linha para travar, o InnoDB trava o intervalo onde ela estaria.
        // Acima do maior id, esse intervalo termina no "supremum pseudo-record".
        assertThat(locks).isNotEmpty();
        assertThat(locks).allSatisfy(lock -> {
            assertThat(lock.indexName()).isEqualTo("PRIMARY");
            assertThat(lock.lockMode()).isNotEqualTo("X,REC_NOT_GAP");
        });
        assertThat(locks).anySatisfy(lock ->
                assertThat(lock.lockData()).isEqualTo("supremum pseudo-record"));
    }

    // Le as travas de registro na tabela accounts por uma conexao separada,
    // como root, porque o usuario da aplicacao nao enxerga o performance_schema.
    private List<RowLock> readAccountRowLocks() {
        var sql = """
                SELECT INDEX_NAME, LOCK_MODE, LOCK_DATA
                FROM performance_schema.data_locks
                WHERE OBJECT_SCHEMA = ?
                  AND OBJECT_NAME = 'accounts'
                  AND LOCK_TYPE = 'RECORD'
                """;

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, mysql.getDatabaseName());

            var locks = new ArrayList<RowLock>();
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    locks.add(new RowLock(result.getString(1), result.getString(2), result.getString(3)));
                }
            }
            return locks;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read performance_schema.data_locks", e);
        }
    }
}
