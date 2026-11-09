package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest sobe só a camada de persistência (entidades, repositories, Flyway),
// sem controllers nem services. Cada teste roda numa transação que é desfeita no fim,
// então um teste não enxerga os dados gravados por outro.
@DataJpaTest
// Sem esta linha o Spring tentaria trocar o MySQL por um banco em memória (H2).
// Queremos testar contra o MySQL de verdade, então mandamos não trocar nada.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// É este import que sobe o MySQL 8.4 no Docker e aponta o datasource para ele.
// Faltava ele: sem o container, o Spring caía no application.properties (localhost + ${DB_USERNAME}).
@Import(TestcontainersConfiguration.class)
class AccountRepositoryTest {

    // CPF válido (dígitos verificadores corretos), já normalizado, como o service grava.
    private static final String VALID_DOCUMENT = "12345678909";

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldGenerateIdWhenAccountIsPersisted() {
        var account = new Account("John Doe", VALID_DOCUMENT);

        // saveAndFlush força o INSERT agora, então o id gerado pelo AUTO_INCREMENT já volta preenchido.
        var savedAccount = accountRepository.saveAndFlush(account);

        assertThat(savedAccount.getId()).isNotNull().isPositive();
    }

    @Test
    void shouldFindAccountById() {
        var savedAccount = accountRepository.saveAndFlush(new Account("John Doe", VALID_DOCUMENT));

        var found = accountRepository.findById(savedAccount.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerName()).isEqualTo("John Doe");
        assertThat(found.get().getDocument()).isEqualTo(VALID_DOCUMENT);
    }

    @Test
    void shouldReturnEmptyWhenAccountDoesNotExist() {
        assertThat(accountRepository.findById(999_999L)).isEmpty();
    }

    @Test
    void shouldReportExistingDocument() {
        accountRepository.saveAndFlush(new Account("John Doe", VALID_DOCUMENT));

        assertThat(accountRepository.existsByDocument(VALID_DOCUMENT)).isTrue();
    }

    @Test
    void shouldReportMissingDocument() {
        assertThat(accountRepository.existsByDocument(VALID_DOCUMENT)).isFalse();
    }

    @Test
    void shouldRejectDuplicateDocuments() {
        accountRepository.saveAndFlush(new Account("John Doe", VALID_DOCUMENT));
        var duplicate = new Account("Jane Doe", VALID_DOCUMENT);

        // Quem barra aqui é a UNIQUE KEY uk_document do banco, não o código Java.
        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
