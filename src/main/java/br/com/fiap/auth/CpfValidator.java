package br.com.fiap.auth;

public final class CpfValidator {

    private CpfValidator() {
    }

    public static String normalizar(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    public static boolean isValido(String valor) {
        String cpf = normalizar(valor);
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }

        try {
            int primeiro = calcularDigito(cpf, 9, 10);
            int segundo = calcularDigito(cpf, 10, 11);
            return primeiro == Character.getNumericValue(cpf.charAt(9))
                    && segundo == Character.getNumericValue(cpf.charAt(10));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int calcularDigito(String cpf, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int indice = 0; indice < tamanho; indice++) {
            int digito = Character.getNumericValue(cpf.charAt(indice));
            soma += digito * (pesoInicial - indice);
        }

        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
    }
}

