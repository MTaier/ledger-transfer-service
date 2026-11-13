# Conta digital com transferências

[![CI](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml/badge.svg)](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml)

API de conta digital em Java e Spring Boot, construída para praticar os problemas reais de um sistema financeiro: ledger de lançamentos, transferências atômicas, concorrência, idempotência e eventos com outbox e Kafka.

> **Status:** em desenvolvimento. Contas, ledger, transferências atômicas com todas as regras de negócio e consulta de saldo estão prontos e testados. Concorrência, idempotência e eventos são as próximas etapas (veja o [roadmap](#roadmap)).

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
        ├── model/              entidades, DTOs e exceções de cada domínio (account, transfer, ledger) e validação de CPF
        └── infra/              tratador global de erros e segurança
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
| Unitário | Validação e normalização de CPF; validação do valor da transferência no DTO; regras dos services (contas, saldo e transferências) com os repositories simulados |
| Integração (banco) | Estrutura das tabelas após as migrations (chaves únicas, FKs e CHECKs); operações dos repositories; cálculo do saldo pelo ledger |
| Integração (atomicidade) | Uma falha simulada no meio da transferência não deixa transferência nem lançamento gravados |
| Integração (API) | Endpoints de ponta a ponta, incluindo saldos após a transferência e, para cada regra, o status, o title e o detail do erro |

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

### Consultar saldo

```http
GET /accounts/{id}/balance
```

Resposta `200 OK`, ou `404` se a conta não existir:

```json
{
  "accountId": 1,
  "balance": 150.00
}
```

O saldo não é uma coluna: é calculado como a soma dos créditos menos a soma dos débitos da conta no ledger. Uma conta sem lançamentos tem saldo `0.00`.

### Criar transferência

```http
POST /transfers
Content-Type: application/json

{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00
}
```

Resposta `201 Created`, com o header `Location: http://localhost:8081/transfers/1`:

```json
{
  "id": 1,
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00,
  "status": "COMPLETED",
  "createdAt": "2026-11-12T16:30:00"
}
```

A transferência só é aceita se as duas contas existirem e estiverem ativas, forem diferentes e a origem tiver saldo suficiente. Ela grava, na mesma transação, o registro da transferência, um débito na origem e um crédito no destino.

### Consultar transferência

```http
GET /transfers/{id}
```

Resposta `200 OK` com o mesmo formato acima, ou `404` se a transferência não existir.

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
| Valor da transferência zero, negativo ou com mais de 2 casas decimais | 400 | Invalid data (com a lista `errors` por campo) |
| CPF com dígitos verificadores inválidos | 400 | Invalid CPF |
| CPF já cadastrado | 409 | CPF already registered |
| Conta inexistente | 404 | Account not found |
| Transferência inexistente | 404 | Transfer not found |
| Origem igual ao destino | 422 | Invalid transfer |
| Saldo insuficiente na origem | 422 | Insufficient balance |
| Conta de origem ou destino inativa ou bloqueada | 422 | Account not active |
| Erro inesperado | 500 | Internal server error |

> `404` fica para o que não existe. Quando a conta existe mas a transferência viola uma regra de negócio (mesma conta, saldo insuficiente, conta inativa ou bloqueada), a resposta é `422`. Nos erros de conta, o `detail` diz qual das duas falhou (por exemplo, `Destination account not found.` ou `Source account is inactive or blocked.`). Conta inativa e conta bloqueada respondem igual, para não expor o motivo do bloqueio a quem tenta transferir.

## Decisões de projeto

- **CPF normalizado e validado.** O CPF é aceito com ou sem máscara, gravado só com os 11 dígitos e validado pelos dígitos verificadores. Sequências repetidas, como `111.111.111-11`, são rejeitadas.
- **CPF mascarado nas respostas.** Por minimização de dados (LGPD), a API nunca devolve os 11 dígitos.
- **Unicidade garantida pelo banco.** O service verifica se o CPF já existe, mas duas requisições simultâneas podem passar por essa verificação ao mesmo tempo. A constraint `UNIQUE` do banco é a garantia final, e só a violação dela vira `409`. Qualquer outra violação de integridade segue como erro inesperado, para não esconder bugs.
- **Validação em duas camadas.** O formato dos dados é validado no DTO (Bean Validation), como o valor positivo com no máximo 2 casas. As regras que exigem cálculo ou consulta ficam no service, na ordem da mais barata para a mais cara: origem igual ao destino (só olha a entrada), contas existentes e ativas, e por fim o saldo.
- **Saldo derivado do ledger.** Não existe coluna de saldo. Cada transferência gera dois lançamentos imutáveis, um débito e um crédito de mesmo valor, e o saldo é a soma dos créditos menos a dos débitos. Assim o saldo nunca diverge do histórico, e a soma dos lançamentos de qualquer transferência é sempre zero.
- **Transferência atômica.** O registro da transferência e os dois lançamentos são gravados na mesma transação. Se qualquer gravação falhar, nada fica no banco; um teste simula a falha depois do débito para provar isso.
- **Exceções de domínio separadas da infraestrutura.** Cada domínio tem suas exceções (`model/account/exception`, `model/transfer/exception`), e só o tratador global, em `infra`, sabe traduzi-las para status HTTP.
- **Schema controlado só pelo Flyway.** O Hibernate roda com `ddl-auto=validate`: ele confere se as entidades batem com as tabelas, mas nunca altera o banco.
- **Id numérico sequencial.** O id da conta é `BIGINT AUTO_INCREMENT`, mais simples e eficiente como chave primária no InnoDB.
- **Segredos fora do código.** Credenciais vêm de variáveis de ambiente; o `.env` está no `.gitignore`.
- **Dinheiro como `BigDecimal` com 2 casas** (`DECIMAL(19,2)` no MySQL). `double` e `float` estão fora, porque não representam centavos com exatidão (`0.1 + 0.2` dá `0.30000000000000004`). Entre `BigDecimal` e centavos em `long`, a escolha foi `BigDecimal`: é o padrão em Java para valores monetários, o valor é lido como dinheiro em toda a pilha (JSON, código e banco) e não exige conversão na entrada e na saída da API. O custo é ter que comparar valores com `compareTo`, e não com `equals`, e controlar a escala.
- **Dois estados de transferência: `COMPLETED` e `REJECTED`.** A transferência roda numa única transação, então nunca existe uma transferência visível "em processamento": ela é gravada concluída ou não é gravada. `COMPLETED` significa que o débito e o crédito foram lançados no ledger. `REJECTED` registra uma transferência recusada por regra de negócio (como saldo insuficiente ou conta bloqueada), sem lançamentos, para auditoria, métricas e para a idempotência devolver a mesma resposta numa nova tentativa. Erros de entrada (valor inválido, campo faltando) devolvem 400 e não são gravados como transferência. Hoje as recusas por regra de negócio ainda não são gravadas como `REJECTED`; isso entra junto com a idempotência.

## Roadmap

- [x] Esqueleto: Docker Compose, Flyway, Testcontainers e CI
- [x] Cadastro e consulta de contas, com validação de CPF e erros em ProblemDetail
- [x] Ledger: transferências com um débito e um crédito na mesma transação; saldo como soma dos lançamentos
- [x] Regras de transferência: valor inválido, mesma conta, conta inexistente, conta inativa ou bloqueada e saldo insuficiente
- [ ] Concorrência: lock pessimista das contas em ordem de id
- [ ] Idempotência com o header `Idempotency-Key` (Redis e banco)
- [ ] Autenticação com JWT
- [ ] Documentação OpenAPI
- [ ] Outbox e Kafka, com um `statement-service` de extrato
- [ ] Observabilidade com Prometheus e Grafana

## Licença

[MIT](LICENSE.md)
