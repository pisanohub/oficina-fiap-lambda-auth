package br.com.fiap.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfValidatorTest {

    @Test
    void deveAceitarCpfValidoSemFormatacao() {
        assertTrue(CpfValidator.isValido("52998224725"));
    }

    @Test
    void deveAceitarCpfValidoFormatado() {
        assertTrue(CpfValidator.isValido("529.982.247-25"));
    }

    @Test
    void deveRemoverFormatacaoDoCpf() {
        assertEquals("52998224725", CpfValidator.normalizar("529.982.247-25"));
    }

    @Test
    void deveRejeitarCpfComDigitoVerificadorIncorreto() {
        assertFalse(CpfValidator.isValido("52998224724"));
    }

    @Test
    void deveRejeitarCpfComTodosOsDigitosIguais() {
        assertFalse(CpfValidator.isValido("11111111111"));
    }

    @Test
    void deveRejeitarCpfVazioOuNulo() {
        assertFalse(CpfValidator.isValido(""));
        assertFalse(CpfValidator.isValido(null));
    }
}
