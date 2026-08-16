package br.com.fiap.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class ClienteRepository {

    private final String url;
    private final String usuario;
    private final String senha;

    public ClienteRepository() {
        String host = obrigatoria("DB_HOST");
        String porta = System.getenv().getOrDefault("DB_PORT", "5432");
        String banco = System.getenv().getOrDefault("DB_NAME", "oficina");

        this.url = "jdbc:postgresql://" + host + ":" + porta + "/" + banco;
        this.usuario = obrigatoria("DB_USERNAME");
        this.senha = obrigatoria("DB_PASSWORD");
    }

    public Optional<Cliente> buscarPorCpf(String cpf) throws Exception {
        String sql = """
                SELECT id, nome, cpf_cnpj, ativo
                FROM tb_clientes
                WHERE cpf_cnpj = ?
                LIMIT 1
                """;

        try (Connection conexao = DriverManager.getConnection(url, usuario, senha);
             PreparedStatement statement = conexao.prepareStatement(sql)) {
            statement.setString(1, cpf);

            try (ResultSet resultado = statement.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }

                return Optional.of(new Cliente(
                        resultado.getLong("id"),
                        resultado.getString("nome"),
                        resultado.getString("cpf_cnpj"),
                        resultado.getBoolean("ativo")
                ));
            }
        }
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variavel obrigatoria ausente: " + nome);
        }
        return valor;
    }
}

