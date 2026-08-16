# Oficina FIAP — Lambda de autenticação por CPF

Este repositório contém a Function Serverless responsável pela autenticação dos clientes da Oficina FIAP. Ele é um dos quatro repositórios independentes exigidos pelo trabalho acadêmico e possui responsabilidade exclusiva sobre a autenticação por CPF.

## Responsabilidade deste repositório

A função executa o seguinte fluxo:

1. recebe o CPF pelo Amazon API Gateway;
2. remove pontos, traços e outros caracteres não numéricos;
3. valida os dígitos verificadores do CPF;
4. consulta o cliente no PostgreSQL gerenciado;
5. verifica se o cadastro existe e está ativo;
6. gera um JWT com validade configurável;
7. devolve o token para consumo das APIs protegidas.

```text
Cliente -> API Gateway -> AWS Lambda -> PostgreSQL/RDS
                              |
                              +-> JWT
```

## Separação dos repositórios

| Repositório | Responsabilidade |
|---|---|
| `oficina_fiap` | Aplicação Spring Boot principal e imagem de contêiner |
| `oficina-fiap-lambda-auth` | Lambda de autenticação por CPF e API Gateway |
| `oficina-fiap-infra-k8s` | Infraestrutura Kubernetes com Terraform |
| `oficina-fiap-infra-database` | Banco gerenciado e infraestrutura relacionada |

O provisionamento do RDS não pertence a este repositório. A Lambda recebe os dados de conexão por variáveis de ambiente configuradas durante o deploy.

## Tecnologias

- Java 21;
- AWS Lambda;
- Amazon API Gateway;
- PostgreSQL;
- JSON Web Token (JWT);
- Maven;
- JUnit 5.

## Estrutura

```text
src/
├── main/java/br/com/fiap/auth/
│   ├── Cliente.java
│   ├── ClienteRepository.java
│   ├── CpfAuthHandler.java
│   ├── CpfValidator.java
│   └── JwtTokenService.java
└── test/java/br/com/fiap/auth/
    └── CpfValidatorTest.java
```

## Contrato da autenticação

### Requisição

```http
POST /auth/cpf
Content-Type: application/json
```

```json
{
  "cpf": "52998224725"
}
```

O campo também aceita um CPF formatado, por exemplo `529.982.247-25`.

### Resposta de sucesso — HTTP 200

```json
{
  "token": "JWT_GERADO",
  "tipo": "Bearer",
  "expiresIn": 3600,
  "clienteId": 1
}
```

### Respostas de erro

| Status | Situação |
|---|---|
| `400` | Corpo ausente ou CPF inválido |
| `403` | Cliente encontrado, porém inativo |
| `404` | Cliente não encontrado |
| `500` | Falha interna, de configuração ou de acesso ao banco |

## Variáveis de ambiente

| Variável | Obrigatória | Descrição | Padrão |
|---|---:|---|---|
| `DB_HOST` | Sim | Endpoint do PostgreSQL/RDS | — |
| `DB_PORT` | Não | Porta do PostgreSQL | `5432` |
| `DB_NAME` | Não | Nome do banco | `oficina` |
| `DB_USERNAME` | Sim | Usuário do banco | — |
| `DB_PASSWORD` | Sim | Senha do banco | — |
| `JWT_SECRET` | Sim | Chave de assinatura com no mínimo 32 caracteres | — |
| `JWT_EXPIRATION_SECONDS` | Não | Validade do token em segundos | `3600` |

Credenciais e segredos não devem ser versionados. Em ambiente AWS, devem ser fornecidos por mecanismos seguros durante o provisionamento.

## Compilação e testes

Pré-requisitos:

- JDK 21;
- Maven 3.9 ou superior.

Execute:

```bash
mvn clean verify
```

O comando compila o código, executa os testes unitários e produz o pacote completo com todas as dependências:

```text
target/oficina-cpf-auth.jar
```

Atualmente a suíte cobre CPF válido formatado e não formatado, normalização, dígito verificador incorreto, sequência repetida e valor vazio/nulo.

## Configuração da AWS Lambda

| Propriedade | Valor |
|---|---|
| Runtime | Java 21 |
| Handler | `br.com.fiap.auth.CpfAuthHandler::handleRequest` |
| Artefato | `target/oficina-cpf-auth.jar` |

A função precisa de conectividade de rede com o PostgreSQL/RDS e das variáveis de ambiente descritas anteriormente.

## Conteúdo do JWT

O token é assinado com `JWT_SECRET` e contém:

- `sub`: CPF normalizado;
- `clienteId`: identificador do cliente;
- `tipo`: `CLIENTE`;
- `ativo`: `true`;
- `iat`: instante de emissão;
- `exp`: instante de expiração.

## Estratégia de branches

Após o commit inicial de estruturação:

- a branch `main` deve permanecer protegida;
- alterações devem ser feitas em branches de trabalho;
- o merge em `main` deve ocorrer por Pull Request;
- o pipeline deve validar a compilação e os testes antes do merge;
- os ambientes de homologação e produção devem possuir deploy automatizado.

O commit inicial diretamente na `main` é necessário apenas para inicializar este repositório, que foi criado completamente vazio. A proteção da branch é configurada após esse primeiro envio.

## CI/CD

O workflow `.github/workflows/ci.yml` executa automaticamente em Pull Requests e em atualizações da branch `main`:

1. baixa o código do repositório;
2. configura o Java 21;
3. executa `mvn clean verify`;
4. compila a Lambda e executa os testes;
5. disponibiliza `oficina-cpf-auth.jar` como artefato da execução.

O job obrigatório para proteção da branch chama-se `Compilar, testar e empacotar`. O merge de um Pull Request deve ser permitido somente quando esse job terminar com sucesso.

O deploy contínuo será executado por um workflow separado depois da inclusão do Terraform específico da Lambda e do API Gateway. Essa separação evita misturar a infraestrutura do banco de dados neste repositório e mantém credenciais fora do código-fonte.

## Terraform da Lambda e do API Gateway

O diretório `terraform/` gerencia exclusivamente:

- a Function AWS Lambda `oficina-cpf-auth`;
- o segredo aleatório usado para assinar o JWT;
- o endpoint `POST /auth/cpf` no Amazon API Gateway;
- a integração e a permissão de invocação entre API Gateway e Lambda.

O Terraform deste repositório não cria RDS, EC2 ou cluster Kubernetes. Endpoint, credenciais do banco, subnets e Security Group são entradas fornecidas pela infraestrutura responsável pelo banco e pela rede.

Para preparar uma execução local, copie apenas o modelo sem segredos:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

Preencha `terraform.tfvars` localmente. Esse arquivo é ignorado pelo Git e nunca deve ser enviado ao repositório.

Validação local:

```bash
mvn clean package
terraform -chdir=terraform init -backend=false
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform validate
```

O workflow `Terraform - Validacao` repete essa verificação automaticamente em Pull Requests que alterem a infraestrutura. O primeiro `apply` deste repositório exige a migração dos recursos que ainda estejam registrados no estado Terraform antigo; executar sem essa migração tentaria criar recursos com nomes já existentes.

### Pipeline de deploy

O workflow `CD - Deploy Lambda AWS` compila, testa, gera o plano Terraform e aplica a Lambda e o API Gateway após mudanças na `main`. O job permanece desabilitado até que a migração do estado seja concluída e a variável `DEPLOY_ENABLED` receba o valor `true`.

Variáveis do GitHub Actions:

| Variável | Exemplo/descrição |
|---|---|
| `DEPLOY_ENABLED` | `false` durante a migração; `true` depois dela |
| `AWS_REGION` | `us-east-1` |
| `TF_STATE_BUCKET` | Bucket S3 exclusivo para o estado Terraform |
| `LAMBDA_SUBNET_IDS` | Lista JSON, por exemplo `["subnet-a","subnet-b"]` |
| `LAMBDA_SECURITY_GROUP_ID` | Security Group autorizado no RDS |
| `DB_HOST` | Endpoint privado do RDS |
| `DB_PORT` | `5432` |
| `DB_NAME` | `oficina` |

Secrets do GitHub Actions:

| Secret | Finalidade |
|---|---|
| `AWS_ACCESS_KEY_ID` | Credencial temporária do AWS Academy |
| `AWS_SECRET_ACCESS_KEY` | Credencial temporária do AWS Academy |
| `AWS_SESSION_TOKEN` | Token temporário obrigatório do laboratório |
| `DB_USERNAME` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha do PostgreSQL |

As três credenciais AWS expiram quando a sessão do laboratório termina e precisam ser atualizadas antes de um novo deploy. Nenhum valor secreto deve ser incluído em commits ou logs.
