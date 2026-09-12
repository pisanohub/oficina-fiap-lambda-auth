# Architecture Decision Records

ADRs registram decisões de arquitetura já adotadas neste repositório. A proposta técnica completa, alternativas e pendências de integração estão na [RFC-001](../rfc-001-autenticacao.md). Os [diagramas de sequência](../diagramas-sequencia.md) descrevem os fluxos correspondentes.

Cada registro usa o mesmo formato: Status, Contexto, Decisão e Consequências.

| ADR | Título | Status |
|---|---|---|
| [ADR-001](adr-001-lambda-authorizer.md) | Uso de Lambda Authorizer para validação de JWT | Aceito |
| [ADR-002](adr-002-autenticacao-por-cpf.md) | Autenticação de cliente por CPF e emissão de JWT | Aceito |
| [ADR-003](adr-003-hmac-segredo-compartilhado.md) | Assinatura HMAC com segredo compartilhado | Aceito |
| [ADR-004](adr-004-validacao-em-duas-camadas.md) | Validação no Gateway e autorização por recurso na aplicação | Aceito |
| [ADR-005](adr-005-proxy-http-aplicacao.md) | Encaminhamento HTTP_PROXY para a URL da aplicação | Aceito |
| [ADR-006](adr-006-vpc-somente-lambda-cpf.md) | VPC apenas na Lambda de autenticação | Aceito |
