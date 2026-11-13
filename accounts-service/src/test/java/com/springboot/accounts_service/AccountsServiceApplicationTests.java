package com.springboot.accounts_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountsServiceApplicationTests {

	@Test
	@DisplayName("Application context starts with all beans configured")
	void contextLoads() {
	}

}
