package com.springboot.accounts_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class FlywayMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void accountsTableIsCreatedByFlyway() {

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts",
                Integer.class);

        assertThat(count).isZero();

    }
}
