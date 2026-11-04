package com.springboot.accounts_service;

import org.springframework.boot.SpringApplication;

public class TestAccountsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AccountsServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
