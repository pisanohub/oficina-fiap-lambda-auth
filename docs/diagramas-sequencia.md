# Diagramas de sequência — autenticação e ordem de serviço

Estes diagramas usam Mermaid e podem ser visualizados diretamente no GitHub. As setas contínuas representam chamadas; as tracejadas, respostas. Os blocos `alt` mostram caminhos alternativos.

Base da documentação: Lambda `d99087c`, Spring API `080b582` e branch de integração JWT `87db07e`, consultadas em 10/09/2026. Os diagramas documentam o código e o contrato de integração; não constituem evidência de deploy ou teste na conta do grupo.

## 1. Autenticação do cliente por CPF

O cliente envia um CPF, a Lambda verifica o cadastro e, se ele estiver ativo, devolve uma credencial temporária. O CPF é normalizado antes da consulta; o banco deve conter o documento no formato compatível com essa busca.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Gateway as API Gateway
    participant Auth as Lambda CPF
    participant DB as PostgreSQL privado
    Cliente->>Gateway: POST /dev/auth/cpf com JSON contendo cpf
    Gateway->>Auth: Evento HTTP com body
    Auth->>Auth: Normalizar e validar digitos do CPF
    alt Corpo ausente ou CPF invalido
        Auth-->>Gateway: 400
        Gateway-->>Cliente: Erro de entrada
    else CPF valido
        Auth->>DB: SELECT em tb_clientes por cpf_cnpj
        alt Falha de conexao ou consulta
            Auth-->>Gateway: 500
            Gateway-->>Cliente: Erro interno
        else Cliente nao encontrado
            DB-->>Auth: Nenhum cadastro
            Auth-->>Gateway: 404
            Gateway-->>Cliente: Cliente nao encontrado
        else Cliente encontrado
            DB-->>Auth: id, nome, cpf_cnpj e ativo
            alt Cliente inativo
                Auth-->>Gateway: 403
                Gateway-->>Cliente: Cliente inativo
            else Cliente ativo
                Auth->>Auth: Assinar JWT com JWT_SECRET configurado
                Auth-->>Gateway: 200 com token, tipo, expiresIn e clienteId
                Gateway-->>Cliente: JWT para chamadas protegidas
            end
        end
    end
```

O endpoint de autenticação é público. JSON malformado cai atualmente no tratamento genérico de erro e retorna 500; não está implementado como 400. A consulta ao RDS exige rede e permissões configuradas pelo grupo.

## 2. Consulta de ordens de serviço com JWT

Exemplo concreto: `GET /dev/api/v1/ordens/cliente/{clienteId}`. O prefixo `/dev` pertence ao stage do Gateway; a aplicação recebe `/api/v1/ordens/cliente/{clienteId}`. A operação listada existe em `OrdemDeServicoController.listarPorCliente`.

O diagrama abaixo mostra uma chamada sem decisão já armazenada em cache. A aceitação na Spring depende do alinhamento de identidade descrito na RFC.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Gateway as API Gateway
    participant Authorizer as Lambda Authorizer
    participant Spring as Spring API no Kubernetes
    participant Servico as Servico de ordens
    participant DB as PostgreSQL
    Cliente->>Gateway: GET /dev/api/v1/ordens/cliente/ID com Bearer JWT
    alt Header ausente ou fora do formato
        Gateway-->>Cliente: Rejeicao antes da integracao
    else Header aceito e sem cache
        Gateway->>Authorizer: authorizationToken e methodArn
        Authorizer->>Authorizer: Validar assinatura, exp e claims de cliente
        alt Token rejeitado
            Authorizer-->>Gateway: Politica Deny
            Gateway-->>Cliente: Acesso negado
        else Token aceito
            Authorizer-->>Gateway: Politica Allow para methodArn e contexto
            Gateway->>Spring: HTTP proxy com caminho e Bearer JWT
            Spring->>Spring: Validar JWT e resolver identidade
            Note over Spring: Integracao de identidade ainda precisa ser alinhada
            alt Identidade ou permissao rejeitada
                Spring-->>Gateway: Rejeicao pela aplicacao
                Gateway-->>Cliente: Resposta de erro
            else Identidade e acesso aceitos
                Spring->>Servico: listarOrdensPorCliente(clienteId)
                Servico->>DB: Consultar dados das ordens
                DB-->>Servico: Resultado
                Servico-->>Spring: Lista de ordens ou erro de negocio
                Spring-->>Gateway: Resposta HTTP da aplicacao
                Gateway-->>Cliente: Lista de ordens ou erro
            end
        end
    end
```

O Authorizer não consulta o banco a cada chamada e não verifica se o ID solicitado pertence ao cliente do token. Essa autorização por recurso deve ser garantida pela aplicação. O contexto retornado pelo Authorizer também não substitui automaticamente a identidade esperada pela Spring.

O Gateway está configurado com cache de 300 segundos. Uma decisão em cache pode dispensar a chamada ao Authorizer. Como a política atual é específica ao `methodArn`, reutilizar o mesmo token em outra rota pode produzir negação implícita. Expiração, troca de chave e múltiplas rotas devem ser testadas levando esse cache em conta; veja a [RFC](rfc-001-autenticacao.md).

## 3. Abertura de uma ordem (contrato da aplicação)

A abertura usa `POST /dev/api/v1/ordens` pelo Gateway. A mesma etapa de autorização do diagrama anterior ocorre antes do controller. Este recorte começa após a identidade ter sido aceita.

```mermaid
sequenceDiagram
    autonumber
    participant Gateway as Gateway apos autorizacao
    participant Controller as OrdemDeServicoController
    participant Servico as OrdemDeServicoService
    participant DB as PostgreSQL
    Gateway->>Controller: POST /api/v1/ordens com CriarOrdemDTO
    Controller->>Controller: Validar DTO
    Controller->>Servico: criarOrdem(dto)
    Note over Servico,DB: Regras e persistencia pertencem a aplicacao
    Servico->>DB: Validar referencias e persistir ordem
    DB-->>Servico: Resultado
    Servico-->>Controller: OrdemDeServicoDTO ou erro
    Controller-->>Gateway: 201 se criada, ou erro de validacao/negocio
```

Não se presume aqui aprovação, pagamento ou transição automática de status. O payload e as regras finais devem ser confirmados com a equipe da aplicação antes da gravação.
