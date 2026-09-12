# ADR-004: Validação no Gateway e autorização por recurso na aplicação

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md).

## Contexto
Um JWT válido prova que o portador passou pela emissão deste componente. Não prova que o `clienteId` da URL ou do corpo da ordem pertence a esse portador. Regras de negócio de ordens de serviço residem na aplicação Spring, em outro repositório.

Se a URL da aplicação for pública, um cliente poderia contornar o API Gateway. Este repositório não controla a exposição do cluster.

## Decisão
Tratar o Lambda Authorizer como primeira barreira, não como substituto da autenticação e autorização da Spring.

- O Gateway rejeita token ausente, malformado, expirado, com assinatura inválida ou sem claims de cliente ativo.
- Chamadas autorizadas seguem com o header `Authorization` original.
- A Spring continua validando o JWT e aplicando regras de acesso por recurso.
- Este repositório não implementa checagem de propriedade da ordem.

## Consequências
- Defesa em profundidade: o Gateway reduz tráfego inválido; a aplicação permanece responsável pelo isolamento entre clientes.
- Integração só está completa quando claims, identidade e papéis coincidirem nas três funções (emissão, Authorizer e filtro Spring).
- Se o backend permanecer acessível diretamente, a segurança não pode depender só do Gateway. A restrição de acesso direto cabe às equipes da aplicação e da infraestrutura Kubernetes.
