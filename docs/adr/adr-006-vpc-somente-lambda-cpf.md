# ADR-006: VPC apenas na Lambda de autenticação

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md). Implementado em `terraform/lambda.tf`.

## Contexto
A consulta a `tb_clientes` ocorre em PostgreSQL/RDS privado. A Lambda de autenticação precisa alcançar esse banco. O Authorizer valida apenas JWT e não consulta o RDS. Colocar as duas funções na VPC aumentaria cold start e dependência de ENI sem benefício para o Authorizer.

Subnets, Security Group, endpoint, porta, nome do banco e credenciais são fornecidos pelo responsável da conta e pelo repositório de banco. Este Terraform não cria RDS.

## Decisão
- `oficina-cpf-auth` recebe `vpc_config` com `lambda_subnet_ids` e `lambda_security_group_id`.
- `oficina-jwt-authorizer` não possui `vpc_config`.
- Credenciais de banco e JWT entram por variáveis de ambiente; valores reais ficam em Secrets do GitHub Actions, não no código.
- A implantação usa a role do laboratório (`LabRole`) e a VPC indicada pelo líder da conta.

## Consequências
- Somente a função que acessa o RDS paga o custo de rede privada.
- O Authorizer permanece mais leve e independente da disponibilidade do banco no caminho de cada request.
- O sucesso do `POST /auth/cpf` depende de Security Group, subnets e RDS ativos na conta de destino. Falhas de rede aparecem como `500`.
- Credenciais temporárias do AWS Academy precisam ser renovadas no pipeline quando a sessão expirar.
