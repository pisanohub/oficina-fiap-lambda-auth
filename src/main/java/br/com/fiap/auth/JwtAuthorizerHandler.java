package br.com.fiap.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.Map;

/**
 * Lambda Authorizer do API Gateway. Ele valida o Bearer token antes que uma
 * requisicao alcance as rotas protegidas da aplicacao principal.
 */
public class JwtAuthorizerHandler implements RequestHandler<APIGatewayCustomAuthorizerEvent, Map<String, Object>> {

    private final JwtTokenService jwtTokenService;

    public JwtAuthorizerHandler() {
        this(new JwtTokenService());
    }

    JwtAuthorizerHandler(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Map<String, Object> handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        String methodArn = event == null ? null : event.getMethodArn();

        try {
            String token = extrairBearerToken(event == null ? null : event.getAuthorizationToken());
            Claims claims = jwtTokenService.validarToken(token);

            if (!"CLIENTE".equals(claims.get("tipo", String.class))
                    || !Boolean.TRUE.equals(claims.get("ativo", Boolean.class))) {
                return politica("cliente-desconhecido", "Deny", methodArn, Map.of());
            }

            String cpf = claims.getSubject();
            Number clienteId = claims.get("clienteId", Number.class);

            return politica(cpf, "Allow", methodArn, Map.of(
                    "cpf", cpf,
                    "clienteId", String.valueOf(clienteId.longValue()),
                    "tipo", "CLIENTE"
            ));
        } catch (Exception exception) {
            if (context != null) {
                context.getLogger().log("Token rejeitado pelo authorizer: " + exception.getClass().getSimpleName());
            }
            return politica("cliente-desconhecido", "Deny", methodArn, Map.of());
        }
    }

    private String extrairBearerToken(String authorizationToken) {
        if (authorizationToken == null || !authorizationToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization Bearer ausente");
        }

        String token = authorizationToken.substring(7).trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("JWT ausente");
        }
        return token;
    }

    private Map<String, Object> politica(String principalId,
                                         String efeito,
                                         String methodArn,
                                         Map<String, Object> contexto) {
        String recurso = methodArn == null || methodArn.isBlank() ? "*" : methodArn;

        Map<String, Object> statement = Map.of(
                "Action", "execute-api:Invoke",
                "Effect", efeito,
                "Resource", recurso
        );

        Map<String, Object> policyDocument = Map.of(
                "Version", "2012-10-17",
                "Statement", List.of(statement)
        );

        return Map.of(
                "principalId", principalId,
                "policyDocument", policyDocument,
                "context", contexto
        );
    }
}
