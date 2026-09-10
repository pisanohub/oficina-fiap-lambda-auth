# RFC-001 — Autenticação por CPF e autorização com JWT

Status: proposta para revisão do grupo, com implementação parcial já existente.
Data: 10/09/2026.
Responsáveis pelo componente: dupla dos Gabrieis.

RFC significa registro de uma proposta técnica: explica o problema, a decisão, as alternativas e as condições para considerá-la concluída. Este documento não representa aprovação coletiva nem evidência de teste integrado.

## Contexto e objetivo

O trabalho pede uma Function Serverless que valide CPF, consulte existência e status do cliente e emita um JWT, além de um API Gateway com rotas protegidas. A solução precisa integrar com o PostgreSQL privado e a aplicação Spring executada pelo grupo na AWS.

Este repositório cuida das duas Lambdas, Gateway, Terraform correspondente e CI/CD. Banco, cluster, regras de ordens e pipeline definitivo da aplicação pertencem aos respectivos responsáveis.

## Decisão proposta

1. Publicar `POST /auth/cpf` para emissão de tokens pela Lambda Java 21.
2. Normalizar CPF, validar dígitos e consultar `tb_clientes` por `cpf_cnpj`. Somente cliente encontrado e ativo recebe token.
3. Assinar JWT com chave HMAC compartilhada, fornecida por configuração, e validade padrão de 3600 segundos.
4. Proteger `ANY /api/{proxy+}` com um Lambda Authorizer do tipo TOKEN, lendo `Authorization: Bearer <JWT>`.
5. Encaminhar chamadas autorizadas por HTTP_PROXY à URL `APP_BASE_URL` da aplicação.
6. Manter a validação na Spring API e suas regras de acesso por recurso. O Authorizer funciona como uma primeira barreira; não substitui regras como impedir que um cliente leia a ordem de outro.

Os [diagramas de sequência](diagramas-sequencia.md) mostram emissão, consulta e abertura de ordem.

## Contrato do token e compatibilidade

| Campo | Lambda atual |
|---|---|
| `sub` | CPF normalizado |
| `clienteId` | ID numérico do cliente |
| `tipo` | `CLIENTE` |
| `ativo` | `true` |
| `iat` | Instante de emissão |
| `exp` | Instante de expiração |

`JwtTokenService` utiliza os bytes UTF-8 do segredo e `signWith(chave)` do JJWT. O algoritmo HMAC é escolhido pela biblioteca conforme a chave; não há fixação explícita em HS256 no código. O token é assinado, não criptografado: seu payload pode ser lido e contém CPF. Não há refresh token nem lista de revogação implementados.

**Pendência confirmada:** na Spring `080b582`, `JwtAuthFilter` interpreta `sub` como username e `UsuarioDetailsService` consulta `findByUsername`, construindo autoridades ADMIN para o usuário encontrado. A Lambda autentica clientes em outra consulta e não demonstra essa correspondência de identidade. Na branch `feature/atualizacao-jwt` (`87db07e`), o token da Spring mantém `sub=username` e adiciona uma claim `cpf`; o filtro espera ambos. O JWT da Lambda não possui essa claim separada.

Portanto, chave igual é necessária, mas insuficiente. O grupo deve definir o mapeamento cliente/usuário e a representação de CPF, validar os papéis e ajustar o consumidor/emissor pertinente. Não se deve dar ADMIN automaticamente a um cliente para fazer o teste passar. O merge dessa branch, isoladamente, não comprova interoperabilidade.

## Segredo compartilhado e deploy

O Terraform recebe `jwt_secret` obrigatório, sensível, com pelo menos 32 caracteres e sem espaços nas extremidades. O workflow lê `secrets.JWT_SECRET` e fornece `TF_VAR_jwt_secret`; ambas as Lambdas recebem a variável de ambiente `JWT_SECRET`.

A equipe configura o mesmo valor e a mesma interpretação de bytes na Spring. O valor real não integra esta RFC. O environment `dev` pode sobrepor um Secret do repositório; conferir a configuração efetivamente usada. O estado e o plano Terraform podem conter o segredo mesmo com `sensitive=true`.

O próximo deploy de um estado antigo pode remover `random_password.jwt` do estado e atualizar variáveis das funções. A troca da chave exige emissão de novos tokens e coordenação com a aplicação. Não executar deploy apenas para revisar esta documentação.

## Conta, rede e acesso ao banco

A implantação combinada será na conta AWS do líder, usando a VPC que alcança o RDS privado. Ele fornece subnets, Security Group autorizado, endpoint/porta/nome do banco, credenciais do banco, role do laboratório, bucket de estado e URL da aplicação. A Lambda de autenticação possui `vpc_config`; o Authorizer não precisa acessar o banco e não possui esse bloco.

As credenciais temporárias do AWS Academy são cadastradas nos Secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN`, com atualização quando expirarem. O pipeline utiliza essas credenciais para atuar na conta de destino. Não é necessário transmitir seus valores em mensagens do grupo.

O proxy atual usa URL alcançável pelo Gateway e não configura VPC Link. Um Service ClusterIP isolado não satisfaz esse contrato. Se o backend puder ser acessado diretamente, sua segurança não pode depender apenas do Gateway. A equipe de infraestrutura deve validar exposição e conectividade.

## Limitações e decisões ainda necessárias

- **CPF sozinho não comprova identidade:** validar o documento e localizar o cadastro atende ao fluxo acadêmico, mas alguém que conheça o CPF pode solicitar token. Para uso real, avaliar prova adicional, como OTP, e proteção contra abuso.
- **Enumeração de clientes:** 404 e 403 distintos revelam existência/status. Avaliar resposta genérica e limites de requisição para além da demonstração.
- **Status pode ficar desatualizado:** o Authorizer valida a claim `ativo` emitida anteriormente, não o status atual no banco.
- **Cache de 300 segundos:** decisões podem ser reutilizadas sem verificar novamente a expiração. A policy atual autoriza somente o ARN solicitado; outra rota com o mesmo token pode receber negação implícita. Proposta para a demonstração: avaliar desabilitar o cache e testar múltiplas rotas. Essa mudança não foi aplicada por esta RFC.
- **Autorização por ordem:** um JWT válido não garante que `clienteId` da URL pertence ao portador; isso requer verificação na Spring.
- **Erros:** JSON malformado atualmente retorna 500 pelo tratamento genérico; melhorar o contrato para 400 é uma evolução pendente.
- **Pipelines da aplicação:** o grupo deve escolher entre os fluxos `deploy.yml` e `deploy-iac.yml`. Esta RFC não escolhe nem altera esses pipelines.

## Alternativas consideradas nesta proposta

| Alternativa | Avaliação |
|---|---|
| Validar somente na Spring | Simplifica o Gateway, mas remove a barreira de autorização implementada nele. |
| Validar somente no Gateway | Exige impedir acesso direto e estabelecer identidade confiável no backend; não resolve sozinho regras de propriedade da ordem. |
| Chaves assimétricas / provedor de identidade | Separa assinatura e verificação e pode oferecer autenticação mais forte; exige mudança de contrato e configuração adicional. |
| Segredo HMAC compartilhado | Compatível com a implementação existente, porém exige coordenação da chave e acesso restrito em todos os componentes. |

São alternativas analisadas nesta proposta; não há registro de votação do grupo sobre elas.

## Critérios de aceite e roteiro para o vídeo

1. Validar build/testes e Terraform no repositório da Lambda.
2. Confirmar rede, banco ativo e cliente de teste com CPF normalizado.
3. Confirmar chave, algoritmo, claims, identidade e papéis compatíveis nas três funções de emissão/validação.
4. Executar POST de CPF ativo: obter 200, JWT e clienteId. Não exibir chaves ou token completo na gravação.
5. Executar GET de ordens do cliente pelo Gateway: obter a resposta real da Spring.
6. Repetir com token adulterado, ausente e expirado; verificar bloqueio, considerando o cache.
7. Testar outro caminho com o mesmo JWT e acesso a ordem de outro cliente; não aceitar acesso indevido.
8. Exercitar CPF inválido (400), cliente inexistente (404) e inativo (403).
9. Se a abertura de OS fizer parte da demonstração, usar o DTO acordado e validar 201 e persistência com a equipe da aplicação.
10. Registrar commit implantado, data, resultados e aprovação do grupo. A demonstração está pendente até esse registro.

## Evidências do código

A documentação oficial da AWS explica que políticas podem ser reutilizadas pelo cache sem nova invocação e precisam abranger os recursos/métodos pretendidos: [Lambda authorizers e cache no API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html). Essa orientação fundamenta a pendência de cache registrada acima.

- [Handler de CPF](../src/main/java/br/com/fiap/auth/CpfAuthHandler.java): validação, consulta e respostas.
- [Repositório de clientes](../src/main/java/br/com/fiap/auth/ClienteRepository.java): consulta parametrizada ao PostgreSQL.
- [Serviço JWT](../src/main/java/br/com/fiap/auth/JwtTokenService.java): assinatura e claims.
- [Authorizer](../src/main/java/br/com/fiap/auth/JwtAuthorizerHandler.java): política e contexto.
- [Gateway](../terraform/api-gateway.tf): rotas, proxy e cache.
- [Lambdas](../terraform/lambda.tf): ambiente e rede.
- [Deploy](../.github/workflows/deploy.yml): entrada de Secrets e Terraform.

Referências locais da aplicação consultadas: `OrdemDeServicoController`, `JwtAuthFilter`, `JwtService` e `UsuarioDetailsService` em `pisanohub/oficina_fiap`, commits identificados no início dos diagramas. Revalidar essas premissas se o grupo atualizar a aplicação.
