# ADR-002: Autenticação de cliente por CPF e emissão de JWT

## Status
Aceito

Origem: [RFC-001](../rfc-001-autenticacao.md). Implementado em `CpfAuthHandler`, `CpfValidator`, `ClienteRepository` e `JwtTokenService`.

## Contexto
O enunciado pede uma Function Serverless que valide CPF, consulte existência e status do cliente e emita um JWT. Os clientes da oficina não possuem um fluxo tradicional de usuário e senha neste componente. O cadastro já existe em `tb_clientes`, no PostgreSQL privado gerenciado por outro repositório.

O CPF sozinho não comprova identidade em um sistema real. Para o fluxo acadêmico, o grupo aceitou localizar o cadastro pelo documento e exigir que o cliente esteja ativo.

## Decisão
Publicar `POST /auth/cpf` na Lambda Java 21 `oficina-cpf-auth`.

1. Normalizar o CPF, removendo caracteres não numéricos.
2. Validar dígitos verificadores.
3. Consultar `tb_clientes` por `cpf_cnpj` com query parametrizada.
4. Emitir JWT somente se o cliente for encontrado e estiver ativo.
5. Devolver `token`, `tipo=Bearer`, `expiresIn` (padrão 3600 segundos) e `clienteId`.

Contrato de respostas: `400` para CPF inválido ou corpo ausente, `404` para cliente inexistente, `403` para cliente inativo e `500` para falha interna. JSON malformado ainda cai no tratamento genérico e pode retornar `500`; corrigir para `400` permanece evolução pendente.

Claims do JWT:

| Campo | Valor |
|---|---|
| `sub` | CPF normalizado |
| `clienteId` | ID numérico do cliente |
| `tipo` | `CLIENTE` |
| `ativo` | `true` |
| `iat` / `exp` | instantes de emissão e expiração |

Não há refresh token nem lista de revogação.

## Consequências
- O contrato de autenticação deste repositório fica explícito e testável de forma isolada.
- Alguém que conheça um CPF cadastrado e ativo pode solicitar token. Uso real exigiria prova adicional (por exemplo OTP) e proteção contra abuso.
- Respostas `404` e `403` distintas permitem enumerar existência e status de clientes.
- A Spring API precisa interpretar as mesmas claims. Na aplicação consultada pela RFC, `sub` é tratado como username e há pendência de claim `cpf`; chave igual não garante interoperabilidade.
