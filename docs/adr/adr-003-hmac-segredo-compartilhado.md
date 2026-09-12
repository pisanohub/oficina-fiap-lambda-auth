# ADR-003: Assinatura HMAC com segredo compartilhado

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md). Implementado em `JwtTokenService` e na variável Terraform `jwt_secret`.

## Contexto
A Lambda emite o JWT e o Authorizer, além da Spring API, precisam verificá-lo. Um provedor de identidade externo ou um par de chaves assimétricas separaria assinatura e verificação com mais rigor, mas alteraria o contrato já existente na aplicação e exigiria configuração adicional no laboratório.

A implementação atual da Spring também espera um segredo simétrico. O grupo precisa coordenar um único valor, a interpretação dos bytes e as claims.

## Decisão
Assinar e verificar o JWT com HMAC e chave compartilhada `JWT_SECRET`.

- Segredo obrigatório, sensível, com no mínimo 32 caracteres e sem espaços nas extremidades.
- Bytes UTF-8 do texto; sem decodificação Base64.
- `signWith(chave)` do JJWT: o algoritmo HMAC é escolhido pela biblioteca conforme o tamanho da chave; o código não fixa HS256 explicitamente.
- Terraform recebe `jwt_secret` e injeta `JWT_SECRET` nas duas Lambdas.
- O workflow de deploy lê `secrets.JWT_SECRET` e fornece `TF_VAR_jwt_secret`.
- A Spring deve usar o mesmo valor e a mesma interpretação da chave.

O token é assinado, não criptografado. O payload pode ser lido e contém CPF.

## Consequências
- Compatível com a implementação existente da aplicação, com menor custo operacional no AWS Academy.
- Exige coordenação da chave entre Lambda, Authorizer e Spring. Troca de chave invalida tokens antigos e precisa considerar o cache de 300 segundos do Authorizer.
- `sensitive=true` oculta a saída normal do Terraform, mas estado e planos ainda podem armazenar o valor.
- Chave igual é necessária e insuficiente: o mapeamento de identidade (`sub`, `cpf`, papéis) ainda precisa ser alinhado pelo grupo.
- Não conceder papel ADMIN a um cliente apenas para o teste integrado passar.
