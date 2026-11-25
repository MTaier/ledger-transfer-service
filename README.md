# Conta digital com transferências

[![CI](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml/badge.svg)](https://github.com/MTaier/ledger-transfer-service/actions/workflows/ci.yml)

API de conta digital em Java e Spring Boot, construída para praticar os problemas reais de um sistema financeiro: ledger de lançamentos, transferências atômicas, concorrência, idempotência e eventos com outbox e Kafka.

> **Status:** em desenvolvimento. Contas, ledger, transferências atômicas com todas as regras de negócio, depósitos pela conta caixa e consulta de saldo estão prontos e testados. Concorrência, idempotência e eventos são as próximas etapas (veja o [roadmap](#roadmap)).

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
| Unitário | Validação e normalização de CPF; validação do valor da transferência e do depósito nos DTOs; regras dos services (contas, saldo, transferências e depósitos) com os repositories simulados |
| Integração (banco) | Estrutura das tabelas após as migrations (chaves únicas, FKs e CHECKs, incluindo o tipo de conta e a conta caixa criada pela migration); operações dos repositories; cálculo do saldo pelo ledger |
| Integração (atomicidade) | Uma falha simulada no meio da transferência não deixa transferência nem lançamento gravados |
| Integração (concorrência) | 50 transferências simultâneas de 10,00 a partir de uma conta com 100,00: exatamente 10 concluídas, 40 recusadas por saldo insuficiente, origem com 0 e destino com 100; e 50 transferências cruzadas (A para B e B para A) ao mesmo tempo, todas concluídas sem deadlock e com os saldos iguais aos iniciais |
| Integração (locks) | As travas que o MySQL registra durante a busca com `FOR UPDATE`: só a linha da conta quando ela existe, e um gap lock quando não existe |
| Integração (timeout do lock) | Com a conta de origem trancada por outra transação, a transferência pela API desiste depois do tempo máximo e responde `409`, sem gravar nada |
| Integração (API) | Endpoints de ponta a ponta, incluindo saldos após a transferência e o depósito, a conta caixa escondida e, para cada regra, o status, o title e o detail do erro |

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

Resposta `200 OK` paginada (10 itens por página por padrão). Só lista contas de cliente; a conta caixa nunca aparece.

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

### Depositar

```http
POST /accounts/{id}/deposits
Content-Type: application/json

{
  "amount": 100.00
}
```

Resposta `201 Created` com o mesmo formato da transferência. O depósito é gravado como uma transferência da conta caixa para a conta `{id}`, então ele também pode ser consultado em `GET /transfers/{id}`.

O valor segue as mesmas regras da transferência (positivo, com no máximo 2 casas decimais). A conta de destino precisa existir e estar ativa.

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
| Conta inexistente (ou o id da conta caixa) | 404 | Account not found |
| Transferência inexistente | 404 | Transfer not found |
| Origem igual ao destino | 422 | Invalid transfer |
| Saldo insuficiente na origem | 422 | Insufficient balance |
| Conta de origem ou destino inativa ou bloqueada | 422 | Account not active |
| Conta ocupada por outra operação por mais de 5 segundos (tempo máximo de espera pela trava) | 409 | Account temporarily unavailable |
| Erro inesperado, incluindo a conta caixa ausente no banco | 500 | Internal server error |

> `404` fica para o que não existe. Quando a conta existe mas a transferência viola uma regra de negócio (mesma conta, saldo insuficiente, conta inativa ou bloqueada), a resposta é `422`. Nos erros de conta, o `detail` diz qual das duas falhou (por exemplo, `Destination account not found.` ou `Source account is inactive or blocked.`). Conta inativa e conta bloqueada respondem igual, para não expor o motivo do bloqueio a quem tenta transferir.

## Decisões de projeto

- **CPF normalizado e validado.** O CPF é aceito com ou sem máscara, gravado só com os 11 dígitos e validado pelos dígitos verificadores. Sequências repetidas, como `111.111.111-11`, são rejeitadas.
- **CPF mascarado nas respostas.** Por minimização de dados (LGPD), a API nunca devolve os 11 dígitos.
- **Unicidade garantida pelo banco.** O service verifica se o CPF já existe, mas duas requisições simultâneas podem passar por essa verificação ao mesmo tempo. A constraint `UNIQUE` do banco é a garantia final, e só a violação dela vira `409`. Qualquer outra violação de integridade segue como erro inesperado, para não esconder bugs.
- **Validação em duas camadas.** O formato dos dados é validado no DTO (Bean Validation), como o valor positivo com no máximo 2 casas. As regras que exigem cálculo ou consulta ficam no service, na ordem da mais barata para a mais cara: origem igual ao destino (só olha a entrada), contas existentes e ativas, e por fim o saldo.
- **Saldo derivado do ledger.** Não existe coluna de saldo. Cada transferência gera dois lançamentos imutáveis, um débito e um crédito de mesmo valor, e o saldo é a soma dos créditos menos a dos débitos. Assim o saldo nunca diverge do histórico, e a soma dos lançamentos de qualquer transferência é sempre zero.
- **Transferência atômica.** O registro da transferência e os dois lançamentos são gravados na mesma transação. Se qualquer gravação falhar, nada fica no banco; um teste simula a falha depois do débito para provar isso.
- **Lock pessimista nas transferências.** Sem trava, duas transferências simultâneas da mesma conta podem ler o mesmo saldo, passar as duas na checagem e deixar a conta negativa. Por isso a transferência busca as duas contas com `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`), que tranca as linhas até o fim da transação, e só lê o saldo depois das duas travas. Uma segunda transferência da mesma conta espera a primeira terminar e já enxerga o saldo atualizado. As contas são trancadas sempre do menor id para o maior, independentemente de qual é a origem: assim, uma transferência de A para B e outra de B para A pedem as travas na mesma ordem e não entram em deadlock. Dois testes de integração provam isso: 50 transferências em paralelo da mesma conta nunca deixam o saldo negativo, e 50 transferências cruzadas entre duas contas terminam todas sem deadlock.
- **O que o lock trava no MySQL.** No InnoDB, um `FOR UPDATE` pode travar mais do que a linha: em buscas por intervalo ou por índice não único, ele trava também os intervalos entre os registros (gap locks), o que bloqueia inserções de outras transações. Aqui a busca é pela chave primária com igualdade, então uma conta existente recebe só uma trava de registro (`X,REC_NOT_GAP` no índice `PRIMARY`), sem intervalo. A exceção é um id que não existe: sem linha para travar, o InnoDB trava o intervalo onde ela estaria. Como os ids crescem em sequência, um id acima do maior trava o intervalo final da tabela, e uma conta criada nesse momento espera a transferência terminar. É uma espera curta, porque a transferência para uma conta inexistente falha logo em seguida, então o custo foi aceito. Um teste lê `performance_schema.data_locks` com a transação aberta para provar os dois casos.
- **Tempo máximo de espera pela trava: 5 segundos.** O padrão do MySQL (`innodb_lock_wait_timeout`) é 50 segundos, longo demais para uma API, enquanto uma transferência segura as travas por milissegundos. O valor é aplicado em cada conexão do pool, pelo `connection-init-sql` do Hikari. A dica de timeout do JPA não foi usada porque o MySQL não tem sintaxe para esperar um tempo definido, e o Hibernate a ignora. Quando o tempo estoura (ou o MySQL derruba uma transação por deadlock), o Spring lança `PessimisticLockingFailureException`, e a API responde `409` com mensagem genérica: a mensagem original traz o SQL e fica só no log. É `409`, e não `422`, porque não há regra de negócio violada: a mesma requisição, repetida um instante depois, passa. E não é `503`, porque o serviço continua no ar.
- **Lock pessimista, e não otimista.** O lock otimista não trava nada: cada linha tem uma coluna de versão, e o `UPDATE` só é aplicado se a versão não mudou desde a leitura. Se mudou, a operação falha e precisa ser repetida. Ele é a melhor escolha quando conflitos são raros e a leitura domina, como na edição de um cadastro: ninguém espera por trava, e o custo só aparece no conflito. O pessimista é a melhor escolha quando o conflito é provável e caro de refazer. Aqui pesaram três motivos. Primeiro, o saldo não é uma coluna da conta, e sim a soma do ledger: a transferência não altera a linha da conta, então não existiria versão para conferir sem passar a atualizar a conta em toda transferência só para isso. Segundo, uma conta muito movimentada (como a de um lojista recebendo vários pagamentos ao mesmo tempo) teria conflitos frequentes, e cada um viraria uma nova tentativa, com mais carga no banco do que uma fila curta de espera. Terceiro, o pessimista dá uma garantia simples de provar: as transferências da mesma conta passam uma de cada vez, e os testes de concorrência mostram isso. O custo é a espera pela trava, limitada pelo tempo máximo de 5 segundos e pela ordem fixa das travas, que evita deadlock.
- **Depósito pela conta caixa.** O dinheiro que entra de fora (um PIX recebido, um boleto pago) passa por uma conta interna, a conta caixa, que representa o mundo externo. Um depósito é uma transferência da conta caixa para a conta do cliente, com um débito na caixa e um crédito no cliente. A alternativa, um endpoint que só grava um crédito, foi descartada: o dinheiro surgiria sem contrapartida, quebrando a partida dobrada, e todo lançamento do ledger já pertence a uma transferência. Com a conta caixa, toda movimentação continua gerando um par de lançamentos que soma zero, e o saldo negativo da caixa mostra quanto dinheiro entrou no sistema. Em troca, a conta caixa é a única que pode ficar negativa, e clientes não podem transferir para ela nem a partir dela. A caixa é identificada pela coluna `account_type` (`CUSTOMER` ou `CASH`), existe uma só, criada por migration, e é a única conta sem CPF; o banco garante por `CHECK` que toda conta de cliente tem CPF. O tipo nunca vem da requisição: toda conta criada pela API é de cliente.
- **Conta caixa invisível pela API.** Para quem usa a API, a conta caixa não existe: ela não aparece na listagem, e qualquer endpoint que receba o id dela (consulta, saldo, depósito e transferência, como origem ou destino) responde `404 Account not found`, exatamente como para um id inexistente. Toda checagem de existência feita a pedido de um cliente pergunta "a conta existe e é de cliente?", então não há caminho esquecido por onde a caixa escape, e a API não confirma que aquele id é especial.
- **Regras do depósito.** O valor segue as regras da transferência e a conta de destino precisa existir (`404`) e estar ativa (`422 Account not active`). O depósito não verifica saldo, porque a origem é sempre a conta caixa, a única que pode ficar negativa. Se a conta caixa não existir no banco, é erro de configuração do sistema, não do cliente: a resposta é `500` com mensagem genérica, e o detalhe fica só no log. O depósito roda na mesma transação que grava a transferência e os dois lançamentos, como a transferência comum.
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
- [x] Depósito pela conta caixa, invisível pela API
- [ ] Concorrência: lock pessimista das contas em ordem de id
- [ ] Idempotência com o header `Idempotency-Key` (Redis e banco)
- [ ] Autenticação com JWT
- [ ] Documentação OpenAPI
- [ ] Outbox e Kafka, com um `statement-service` de extrato
- [ ] Observabilidade com Prometheus e Grafana

## Licença

[MIT](LICENSE.md)
