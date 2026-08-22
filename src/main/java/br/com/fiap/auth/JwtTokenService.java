package br.com.fiap.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtTokenService {

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public JwtTokenService() {
        this(System.getenv("JWT_SECRET"), Long.parseLong(
                System.getenv().getOrDefault("JWT_EXPIRATION_SECONDS", "3600")
        ));
    }

    JwtTokenService(String segredo, long expiracaoSegundos) {
        if (segredo == null || segredo.length() < 32) {
            throw new IllegalStateException("JWT_SECRET deve possuir pelo menos 32 caracteres");
        }
        if (expiracaoSegundos <= 0) {
            throw new IllegalArgumentException("A expiracao do JWT deve ser maior que zero");
        }

        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoSegundos = expiracaoSegundos;
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

    public Claims validarToken(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }
}
