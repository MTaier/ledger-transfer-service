# Conta digital com transferências

[![CI](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml/badge.svg)](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml)

API de conta digital em Java e Spring Boot, construída para praticar os problemas reais de um sistema financeiro: ledger de lançamentos, transferências atômicas, concorrência, idempotência e eventos com outbox e Kafka.

> **Status:** em desenvolvimento. O cadastro e a consulta de contas estão prontos e testados. Ledger, transferências e eventos são as próximas etapas (veja o [roadmap](#roadmap)).

## Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, Actuator) |
| Banco de dados | MySQL 8.4 (InnoDB) |
| Migrations | Flyway |
| Cache | Redis 7 (reservado para a idempotência) |
| Testes | JUnit 5, Mockito, AssertJ, MockMvc e Testcontainers |
| Build e CI | Maven Wrapper e GitHub Actions |

## Estrutura

```
conta-digital/
├── docker-compose.yml          MySQL e Redis para desenvolvimento
├── .env.example                variáveis de ambiente necessárias
└── accounts-service/
    └── src/main/java/.../accounts_service/
        ├── controller/         endpoints REST
        ├── service/            regras de negócio
        ├── repository/         acesso a dados (Spring Data JPA)
        ├── model/              entidades, DTOs e validação de CPF
        └── infra/              tratamento global de erros e segurança
```

Os testes em `src/test` espelham os mesmos pacotes.

## Como rodar

**Pré-requisitos:** Java 21 e Docker.

1. Copie o arquivo de variáveis e defina a senha do banco:

   ```bash
   cp .env.example .env
   ```

   O compose cria apenas o usuário `root` do MySQL, então use `DB_USERNAME=root` e a senha que preferir em `DB_PASSWORD`.

2. Suba o MySQL e o Redis:

   ```bash
   docker compose up -d
   ```

3. Rode a aplicação com as mesmas variáveis de ambiente. A aplicação não lê o arquivo `.env`; defina as variáveis no terminal ou na configuração de execução da IDE.

   ```powershell
   # PowerShell (Windows)
   $env:DB_USERNAME="root"; $env:DB_PASSWORD="sua-senha"
   cd accounts-service
   .\mvnw.cmd spring-boot:run
   ```

   ```bash
   # Linux / macOS
   export DB_USERNAME=root DB_PASSWORD=sua-senha
   cd accounts-service
   ./mvnw spring-boot:run
   ```

A API sobe em `http://localhost:8081`. O Flyway cria as tabelas na primeira execução.

## Testes

Os testes de integração usam Testcontainers, que sobe um MySQL 8.4 descartável. Basta ter o Docker rodando; não é preciso subir o compose nem definir variáveis.

```bash
cd accounts-service
./mvnw verify            # Windows: .\mvnw.cmd verify
```

| Tipo | O que cobre |
| --- | --- |
| Unitário | Validação e normalização de CPF; regras do service com o repository simulado |
| Integração (banco) | Estrutura da tabela após as migrations; operações do repository |
| Integração (API) | Endpoints de ponta a ponta, incluindo o formato dos erros |

O CI roda o mesmo `mvnw verify` a cada push na `main` e a cada pull request.

## API

### Criar conta

```http
POST /accounts
Content-Type: application/json

{
  "ownerName": "John Doe",
  "document": "884.612.760-94"
}
```

Resposta `201 Created`, com o header `Location: http://localhost:8081/accounts/1`:

```json
{
  "id": 1,
  "ownerName": "John Doe",
  "document": "***.612.760-**"
}
```

### Consultar conta

```http
GET /accounts/{id}
```

Resposta `200 OK` com o mesmo formato acima, ou `404` se a conta não existir.

### Listar contas

```http
GET /accounts?page=0&size=10
```

Resposta `200 OK` paginada (10 itens por página por padrão).

### Erros

Todos os erros seguem o padrão **ProblemDetail** ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

```json
{
  "type": "about:blank",
  "title": "CPF already registered",
  "status": 409,
  "detail": "An account with this CPF already exists.",
  "instance": "/accounts"
}
```

| Situação | Status | Title |
| --- | --- | --- |
| Campo obrigatório vazio, nome longo demais ou CPF fora do formato | 400 | Invalid data (com a lista `errors` por campo) |
| CPF com dígitos verificadores inválidos | 400 | Invalid CPF |
| CPF já cadastrado | 409 | CPF already registered |
| Conta inexistente | 404 | Account not found |
| Erro inesperado | 500 | Internal server error |

## Decisões de projeto

- **CPF normalizado e validado.** O CPF é aceito com ou sem máscara, gravado só com os 11 dígitos e validado pelos dígitos verificadores. Sequências repetidas, como `111.111.111-11`, são rejeitadas.
- **CPF mascarado nas respostas.** Por minimização de dados (LGPD), a API nunca devolve os 11 dígitos.
- **Unicidade garantida pelo banco.** O service verifica se o CPF já existe, mas duas requisições simultâneas podem passar por essa verificação ao mesmo tempo. A constraint `UNIQUE` do banco é a garantia final, e só a violação dela vira `409`. Qualquer outra violação de integridade segue como erro inesperado, para não esconder bugs.
- **Validação em duas camadas.** O formato dos dados é validado no DTO (Bean Validation). As regras que exigem cálculo ou consulta ficam no service.
- **Schema controlado só pelo Flyway.** O Hibernate roda com `ddl-auto=validate`: ele confere se as entidades batem com as tabelas, mas nunca altera o banco.
- **Id numérico sequencial.** O id da conta é `BIGINT AUTO_INCREMENT`, mais simples e eficiente como chave primária no InnoDB.
- **Segredos fora do código.** Credenciais vêm de variáveis de ambiente; o `.env` está no `.gitignore`.

## Roadmap

- [x] Esqueleto: Docker Compose, Flyway, Testcontainers e CI
- [x] Cadastro e consulta de contas, com validação de CPF e erros em ProblemDetail
- [ ] Ledger: transferências com um débito e um crédito na mesma transação; saldo como soma dos lançamentos
- [ ] Regras de transferência: saldo insuficiente, mesma conta, valor inválido, conta inativa
- [ ] Concorrência: lock pessimista das contas em ordem de id
- [ ] Idempotência com o header `Idempotency-Key` (Redis e banco)
- [ ] Autenticação com JWT
- [ ] Documentação OpenAPI
- [ ] Outbox e Kafka, com um `statement-service` de extrato
- [ ] Observabilidade com Prometheus e Grafana

## Licença

[MIT](LICENSE.md)
