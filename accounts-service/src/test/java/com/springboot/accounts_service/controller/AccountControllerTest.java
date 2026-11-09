package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAccount() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerName": "John Doe", "document": "884.612.760-94"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.document").value("***.612.760-**"));
    }

    @Test
    void shouldRejectInvalidCpf() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"ownerName": "John Doe", "document": "884.612.760-95"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CPF"));
    }

    @Test
    void shouldRejectBlankFields() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerName": "", "document": "884.612.760-94"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ownerName").exists())
                .andExpect(jsonPath("$.title").value("Invalid data"));

    }

    @Test
    void shouldRejectDuplicateCpf() throws Exception {
        var body = """
            {"ownerName": "John Doe", "document": "884.612.760-94"}
            """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("CPF already registered"));
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() throws Exception {
        mockMvc.perform(get("/accounts/999999"))
                .andExpect(status().isNotFound());
    }

}
