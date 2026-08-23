package br.com.fiap.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthorizerHandlerTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";
    private static final String ARN = "arn:aws:execute-api:us-east-1:123456789012:api/dev/GET/api/v1/cliente";

    @Test
    void devePermitirTokenDeClienteValido() {
        JwtTokenService service = new JwtTokenService(SEGREDO, 3600);
        String token = service.gerarToken(new Cliente(7L, "Cliente", "52998224725", true));
        JwtAuthorizerHandler handler = new JwtAuthorizerHandler(service);

        Map<String, Object> resposta = handler.handleRequest(evento("Bearer " + token), null);

        assertEquals("52998224725", resposta.get("principalId"));
        assertEquals("Allow", efeito(resposta));
        assertEquals("7", contexto(resposta).get("clienteId"));
    }

    @Test
    void deveNegarTokenAusenteOuInvalido() {
        JwtAuthorizerHandler handler = new JwtAuthorizerHandler(new JwtTokenService(SEGREDO, 3600));

        assertEquals("Deny", efeito(handler.handleRequest(evento(null), null)));
        assertEquals("Deny", efeito(handler.handleRequest(evento("Bearer token-invalido"), null)));
    }

    private APIGatewayCustomAuthorizerEvent evento(String token) {
        APIGatewayCustomAuthorizerEvent event = new APIGatewayCustomAuthorizerEvent();
        event.setAuthorizationToken(token);
        event.setMethodArn(ARN);
        return event;
    }

    @SuppressWarnings("unchecked")
    private String efeito(Map<String, Object> resposta) {
        Map<String, Object> documento = (Map<String, Object>) resposta.get("policyDocument");
        List<Map<String, Object>> statements = (List<Map<String, Object>>) documento.get("Statement");
        return (String) statements.getFirst().get("Effect");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contexto(Map<String, Object> resposta) {
        return (Map<String, Object>) resposta.get("context");
    }
}
