# ADR-001: Uso de Lambda Authorizer para validação de JWT

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md). Implementado em `JwtAuthorizerHandler` e `terraform/api-gateway.tf`.

## Contexto
Precisávamos proteger as rotas sensíveis da API sem duplicar a lógica de validação de token em múltiplos lugares do Gateway. O trabalho exige um API Gateway com rotas protegidas e uma Function Serverless que valide o JWT antes da aplicação principal. `POST /auth/cpf` precisa permanecer público para emissão do token; as demais rotas do cliente, sob `/api/{proxy+}`, não.

Validar somente na Spring simplificaria o Gateway, mas removeria a barreira de autorização pedida no enunciado. Validar somente no Gateway exigiria impedir acesso direto ao backend e ainda não cobriria regras de propriedade da ordem.

## Decisão
Optamos por implementar um Lambda Authorizer customizado no API Gateway, que valida o JWT antes de qualquer requisição chegar à aplicação principal.

- Tipo `TOKEN`, lendo `Authorization: Bearer <JWT>`.
- Expressão de identidade `^Bearer [-0-9A-Za-z._~+/]+=*$`.
- Handler `br.com.fiap.auth.JwtAuthorizerHandler::handleRequest`.
- Validação de assinatura, expiração e claims `tipo=CLIENTE` e `ativo=true`.
- Policy IAM `Allow` ou `Deny` restrita ao `methodArn` solicitado.
- Cache do resultado por 300 segundos (`authorizer_result_ttl_in_seconds`).
- Authorizer sem acesso ao banco: não consulta o status atual do cliente.

O endpoint `POST /auth/cpf` permanece com `authorization = NONE`.

## Consequências
- Centraliza a validação de autenticação num único ponto na borda do Gateway.
- Adiciona uma chamada extra de rede a cada requisição ainda não coberta pelo cache.
- A aplicação principal ainda faz validação complementar de autorização por recurso (ex.: cliente só vê as próprias ordens).
- O Authorizer não reconsulta o banco; a claim `ativo` reflete o status no momento da emissão.
- Políticas em cache podem ser reutilizadas sem nova invocação. Como a policy autoriza somente o ARN pedido, o mesmo token em outra rota pode receber negação implícita. A RFC propõe avaliar desabilitar o cache para a demonstração; essa mudança ainda não foi aplicada.
