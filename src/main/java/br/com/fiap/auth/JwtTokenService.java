package br.com.fiap.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtTokenService {

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public JwtTokenService() {
        String segredo = System.getenv("JWT_SECRET");
        if (segredo == null || segredo.length() < 32) {
            throw new IllegalStateException("JWT_SECRET deve possuir pelo menos 32 caracteres");
        }

        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoSegundos = Long.parseLong(
                System.getenv().getOrDefault("JWT_EXPIRATION_SECONDS", "3600")
        );
    }

    public String gerarToken(Cliente cliente) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(cliente.cpf())
                .claim("clienteId", cliente.id())
                .claim("tipo", "CLIENTE")
                .claim("ativo", true)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(expiracaoSegundos)))
                .signWith(chave)
                .compact();
    }
}

