# Oficina FIAP — Lambda de autenticação por CPF

Este repositório contém as Functions Serverless responsáveis pela autenticação dos clientes da Oficina FIAP e o API Gateway que protege as rotas destinadas ao cliente. Ele é um dos quatro repositórios independentes exigidos pelo trabalho acadêmico.

## Limite de responsabilidade

Este repositório gerencia somente:

- autenticação do cliente por CPF;
- emissão e validação de JWT;
- Lambda Authorizer;
- API Gateway e o proxy das rotas protegidas;
- CI/CD deste componente.

Ele **não cria nem modifica** RDS, EC2, cluster Kubernetes, manifests da aplicação ou regras de negócio. O time responsável pela aplicação fornece apenas uma URL pública alcançável pelo API Gateway.

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
1. Emissão do token

Cliente -> POST /auth/cpf -> Lambda CPF -> PostgreSQL/RDS
                                      |
                                      +-> JWT

2. Consumo de rota protegida

Cliente -> /api/{proxy+} -> Lambda Authorizer -> aplicação principal
              Bearer JWT        Allow/Deny       (URL fornecida pelo time Kubernetes)
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

## Conceitos em linguagem simples

- **Function Serverless:** código executado sob demanda na nuvem. A equipe envia a função, e a AWS administra os servidores necessários para executá-la.
- **AWS Lambda:** serviço da AWS usado para executar as Functions Serverless. Este projeto possui uma Lambda para autenticar pelo CPF e outra para autorizar as requisições.
- **API Gateway:** porta de entrada HTTP do sistema. Ele publica as URLs, recebe as chamadas e decide para onde encaminhá-las.
- **Lambda Authorizer:** função chamada pelo API Gateway antes de uma rota protegida. Ela confere o JWT e responde `Allow` quando o acesso pode continuar ou `Deny` quando deve ser bloqueado.
- **JWT:** token assinado devolvido após a autenticação. Ele funciona como uma credencial temporária e precisa ser enviado no header `Authorization`.

Uma analogia simples: o **API Gateway é a portaria**, a Lambda de CPF **entrega um crachá temporário**, e o Lambda Authorizer **confere esse crachá** antes de liberar uma rota protegida.

## Estrutura

```text
src/
├── main/java/br/com/fiap/auth/
│   ├── Cliente.java
│   ├── ClienteRepository.java
│   ├── CpfAuthHandler.java
│   ├── CpfValidator.java
│   ├── JwtAuthorizerHandler.java
│   └── JwtTokenService.java
└── test/java/br/com/fiap/auth/
    ├── CpfValidatorTest.java
    ├── JwtAuthorizerHandlerTest.java
    └── JwtTokenServiceTest.java
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

A suíte contém 11 testes e cobre CPF válido formatado e não formatado, normalização, dígito verificador, geração e validação do JWT, segredo inválido, policy `Allow` e rejeição `Deny` do authorizer.

## Como executar esta parte do projeto

### 1. Pré-requisitos

Instale e confira:

- Git;
- JDK 21;
- Maven 3.9 ou superior;
- Terraform 1.13 ou compatível;
- AWS CLI, somente para deploy na AWS;
- Postman, opcional para testar os endpoints.

No PowerShell:

```powershell
java -version
mvn -version
terraform -version
aws --version
```

### 2. Baixar o repositório

```powershell
cd C:\CODEX
git clone https://github.com/pisanohub/oficina-fiap-lambda-auth.git
cd oficina-fiap-lambda-auth
```

Se o repositório já estiver baixado:

```powershell
git switch main
git pull
```

### 3. Compilar e executar os testes localmente

Este passo não acessa a AWS e não cria recursos:

```powershell
mvn clean verify
```

O resultado esperado é `BUILD SUCCESS`. O arquivo enviado às Lambdas será criado em:

```text
target/oficina-cpf-auth.jar
```

### 4. Validar o Terraform sem criar recursos

```powershell
terraform -chdir=terraform init -backend=false
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform validate
```

O resultado esperado do último comando é `Success! The configuration is valid.` Esse procedimento valida a sintaxe, mas não executa deploy.

### 5. Obter as informações das outras infraestruturas

Antes de implantar, este repositório precisa receber das equipes responsáveis:

- endpoint, porta, nome, usuário e senha do PostgreSQL;
- pelo menos duas subnets para a Lambda de autenticação;
- Security Group com acesso ao PostgreSQL;
- URL pública da aplicação principal em `APP_BASE_URL`, sem `/api` no final;
- bucket S3 usado como backend do estado Terraform.

Esses recursos não devem ser recriados neste repositório.

### 6. Fazer o deploy pelo GitHub Actions

O caminho recomendado é o workflow **CD - Deploy Lambda AWS**:

1. acesse `Settings > Secrets and variables > Actions` no GitHub;
2. cadastre as variáveis e secrets descritos na seção **Pipeline de deploy**;
3. inicie o AWS Academy Learner Lab;
4. atualize `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` com as credenciais da sessão atual;
5. mantenha `DEPLOY_ENABLED=false` enquanto estiver apenas validando;
6. altere para `DEPLOY_ENABLED=true` somente quando todos os valores externos estiverem corretos;
7. acesse `Actions > CD - Deploy Lambda AWS > Run workflow`;
8. acompanhe os passos de build, testes, plano e apply.

> O deploy cria ou atualiza recursos na AWS e pode consumir créditos. Ao terminar uma demonstração, o grupo deve seguir seu procedimento combinado de desligamento ou destruição da infraestrutura.

### 7. Descobrir a URL implantada

Ao final do workflow, a URL aparece no resumo da execução. Também é possível obtê-la na máquina configurada com o mesmo backend Terraform:

```powershell
terraform -chdir=terraform output -raw api_url
```

Exemplo de resultado:

```text
https://abc123.execute-api.us-east-1.amazonaws.com/dev/auth/cpf
```

### 8. Testar a autenticação no Postman

Crie uma requisição `POST` para a URL retornada, selecione **Body > raw > JSON** e envie:

```json
{
  "cpf": "52998224725"
}
```

Se o CPF for válido e o cliente estiver ativo no banco, a resposta será semelhante a:

```json
{
  "token": "eyJ...",
  "tipo": "Bearer",
  "expiresIn": 3600,
  "clienteId": 1
}
```

### 9. Testar uma rota protegida

Use a base protegida mostrada no output `protected_api_base_url`, acrescente o caminho real da aplicação e envie o JWT:

```http
GET https://abc123.execute-api.us-east-1.amazonaws.com/dev/api/rota-da-aplicacao
Authorization: Bearer eyJ...
```

No Postman, selecione **Authorization > Bearer Token** e cole somente o valor de `token`. Com token válido, o Gateway encaminha a chamada. Sem token, com assinatura incorreta ou após a expiração, o Authorizer bloqueia o acesso.

### 10. O que pode ser executado sem a AWS

Localmente é possível:

- compilar o JAR;
- executar todos os testes unitários;
- validar a formatação e a configuração Terraform;
- estudar a geração e a validação do JWT pelos testes.

O fluxo completo de API Gateway, Lambda e consulta ao RDS depende do ambiente AWS e das informações fornecidas pelas outras infraestruturas.

## Configuração da AWS Lambda

| Propriedade | Valor |
|---|---|
| Runtime | Java 21 |
| Handler de autenticação | `br.com.fiap.auth.CpfAuthHandler::handleRequest` |
| Handler do authorizer | `br.com.fiap.auth.JwtAuthorizerHandler::handleRequest` |
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

## Proteção das rotas com Lambda Authorizer

O endpoint `POST /auth/cpf` é público porque é usado para obter o token. As rotas sob `/api/{proxy+}` usam autorização `CUSTOM` do API Gateway.

Para cada token ainda não armazenado no cache do Gateway, o fluxo é:

1. o API Gateway lê o header `Authorization`;
2. exige o formato `Bearer <JWT>`;
3. invoca `oficina-jwt-authorizer`;
4. o authorizer valida assinatura e expiração;
5. confirma as claims `tipo=CLIENTE` e `ativo=true`;
6. devolve uma policy IAM `Allow` ou `Deny`;
7. em caso de `Allow`, o Gateway encaminha a chamada para `${APP_BASE_URL}/api/{proxy}`.

Exemplo:

```http
GET /dev/api/v1/ordens/cliente/1
Authorization: Bearer JWT_GERADO
```

O authorizer não consulta novamente o banco. A existência e o status do cliente já foram verificados durante a emissão, e a validade curta do token limita por quanto tempo essa informação é aceita.

> O API Gateway só consegue encaminhar chamadas para uma URL alcançável a partir da AWS. Um `Service` Kubernetes do tipo `ClusterIP` não é público; a equipe responsável pelo cluster deve fornecer Ingress, Load Balancer ou outro endpoint apropriado. Este repositório apenas recebe essa URL como entrada e não altera o cluster.

> **Contrato de segurança:** se a URL da aplicação continuar pública e aceitar chamadas diretas, um cliente poderia contornar o API Gateway. A equipe da aplicação/cluster deve restringir o acesso direto ao backend ou manter a validação JWT também na aplicação. A implementação dessa restrição não pertence a este repositório.

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

O deploy contínuo é executado pelo workflow separado `.github/workflows/deploy.yml`. A separação entre CI, validação Terraform e deploy evita misturar responsabilidades e mantém credenciais fora do código-fonte.

## Terraform da Lambda e do API Gateway

O diretório `terraform/` gerencia exclusivamente:

- a Function AWS Lambda `oficina-cpf-auth`;
- a Function AWS Lambda `oficina-jwt-authorizer`;
- o segredo aleatório usado para assinar o JWT;
- o endpoint `POST /auth/cpf` no Amazon API Gateway;
- o Lambda Authorizer;
- o proxy protegido `/api/{proxy+}`;
- as integrações e permissões de invocação necessárias.

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

O workflow `Terraform - Validacao` repete essa verificação automaticamente em Pull Requests que alterem a infraestrutura. Antes de habilitar o primeiro `apply`, o grupo deve configurar um backend S3 novo e fornecer as entradas da rede, do banco e da aplicação. O pipeline não cria esses recursos externos.

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
| `APP_BASE_URL` | URL pública da aplicação, sem `/api` no final |

Secrets do GitHub Actions:

| Secret | Finalidade |
|---|---|
| `AWS_ACCESS_KEY_ID` | Credencial temporária do AWS Academy |
| `AWS_SECRET_ACCESS_KEY` | Credencial temporária do AWS Academy |
| `AWS_SESSION_TOKEN` | Token temporário obrigatório do laboratório |
| `DB_USERNAME` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha do PostgreSQL |

As três credenciais AWS expiram quando a sessão do laboratório termina e precisam ser atualizadas antes de um novo deploy. Nenhum valor secreto deve ser incluído em commits ou logs.

## Como explicar esta entrega ao grupo

- **CPF não é um token:** ele é a informação usada para localizar e validar o cliente.
- **A Lambda de autenticação emite o JWT:** ela valida o documento, consulta o cadastro e assina as claims.
- **O Authorizer valida o JWT:** ele funciona como porteiro antes das rotas protegidas.
- **O API Gateway aplica a decisão:** somente uma policy `Allow` alcança a aplicação principal.
- **O mesmo segredo liga emissão e validação:** as duas Lambdas recebem o mesmo `JWT_SECRET` gerado pelo Terraform.
- **A aplicação continua pertencendo ao outro time:** este repositório apenas encaminha a requisição para `APP_BASE_URL`.
