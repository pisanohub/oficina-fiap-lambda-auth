package br.com.fiap.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";

    @Test
    void deveGerarEValidarTokenDeCliente() {
        JwtTokenService service = new JwtTokenService(SEGREDO, 3600);
        Cliente cliente = new Cliente(42L, "Cliente Teste", "52998224725", true);

        Claims claims = service.validarToken(service.gerarToken(cliente));

        assertEquals("52998224725", claims.getSubject());
        assertEquals(42, claims.get("clienteId", Number.class).intValue());
        assertEquals("CLIENTE", claims.get("tipo", String.class));
        assertTrue(claims.get("ativo", Boolean.class));
    }

    @Test
    void deveRejeitarTokenAssinadoComOutroSegredo() {
        JwtTokenService emissor = new JwtTokenService(SEGREDO, 3600);
        JwtTokenService validador = new JwtTokenService(
                "outro-segredo-de-teste-com-mais-de-trinta-e-dois-caracteres", 3600
        );
        String token = emissor.gerarToken(new Cliente(1L, "Teste", "52998224725", true));

        assertThrows(Exception.class, () -> validador.validarToken(token));
    }

    @Test
    void deveRejeitarConfiguracaoInsegura() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenService("curto", 3600));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(SEGREDO, 0));
    }
}
