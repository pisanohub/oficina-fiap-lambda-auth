# ADR-005: Encaminhamento HTTP_PROXY para a URL da aplicação

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md). Implementado em `terraform/api-gateway.tf`.

## Contexto
Este repositório não cria cluster, Ingress nem Service Kubernetes. A aplicação Spring é implantada por outro time, que deve fornecer uma URL alcançável pelo API Gateway. Um `Service` do tipo `ClusterIP` isolado não atende a esse contrato.

VPC Link permitiria integrar o Gateway a um NLB interno, mas exigiria recursos de rede que não pertencem a este componente e não foram fornecidos como entrada estável do laboratório.

## Decisão
Usar integração `HTTP_PROXY` em `ANY /api/{proxy+}` para `${APP_BASE_URL}/api/{proxy}`.

- `APP_BASE_URL` é variável de entrada, sem `/api` no final.
- Não configurar VPC Link neste Terraform.
- Não alterar manifests ou o cluster a partir daqui.

O time de infraestrutura/aplicação deve fornecer Ingress, Load Balancer ou outro endpoint público (ou alcançável pela AWS) e validar conectividade.

## Consequências
- O Gateway funciona como fachada única para o cliente: autenticação pública em `/auth/cpf` e proxy autenticado para a API.
- Dependência explícita de uma URL estável fornecida por outro repositório.
- Se essa URL for pública, o Authorizer pode ser contornado; a Spring precisa continuar validando o JWT.
- Testes integrados só fazem sentido depois que a URL, a rede e o contrato HTTP da aplicação estiverem confirmados.
