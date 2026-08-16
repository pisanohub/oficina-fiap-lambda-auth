package br.com.fiap.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

public class CpfAuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRepository repository = new ClienteRepository();
    private final JwtTokenService jwtService = new JwtTokenService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (request.getBody() == null || request.getBody().isBlank()) {
                return resposta(400, Map.of("erro", "Corpo da requisicao ausente"));
            }

            JsonNode corpo = JSON.readTree(request.getBody());
            String cpf = CpfValidator.normalizar(corpo.path("cpf").asText(""));

            if (!CpfValidator.isValido(cpf)) {
                return resposta(400, Map.of("erro", "CPF invalido"));
            }

            Optional<Cliente> encontrado = repository.buscarPorCpf(cpf);
            if (encontrado.isEmpty()) {
                return resposta(404, Map.of("erro", "Cliente nao encontrado"));
            }

            Cliente cliente = encontrado.get();
            if (!cliente.ativo()) {
                return resposta(403, Map.of("erro", "Cliente inativo"));
            }

            return resposta(200, Map.of(
                    "token", jwtService.gerarToken(cliente),
                    "tipo", "Bearer",
                    "expiresIn", 3600,
                    "clienteId", cliente.id()
            ));
        } catch (Exception exception) {
            context.getLogger().log("Erro na autenticacao: " + exception.getMessage());
            return resposta(500, Map.of("erro", "Erro interno de autenticacao"));
        }
    }

    private APIGatewayProxyResponseEvent resposta(int status, Map<String, Object> corpo) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(JSON.writeValueAsString(corpo));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

