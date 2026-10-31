package com.autoflow.config.security.service;

import com.autoflow.infrastructure.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "1234567890123456789012345678901234567890123456789012345678901234";

    @BeforeEach
    void setup() {

        jwtService = new JwtService();

        ReflectionTestUtils.setField(
                jwtService,
                "secret",
                SECRET
        );

        ReflectionTestUtils.setField(
                jwtService,
                "expiration",
                3600000L
        );
    }

    @Test
    void deveGerarToken() {

        String token =
                jwtService.gerarToken(
                        "52998224725",
                        "ROLE_ADMIN"
                );

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
        void deveExtrairCpfCnpjDoToken() {

        String token =
                jwtService.gerarToken(
                        "52998224725",
                        "ROLE_ADMIN"
                );

        String cpfCnpj =
                jwtService.extrairCpfCnpj(token);

        assertEquals(
                "52998224725",
                cpfCnpj
        );
    }

    @Test
    void deveExtrairRoleDoToken() {

        String token =
                jwtService.gerarToken(
                        "52998224725",
                        "ROLE_ADMIN"
                );

        String role =
                jwtService.extrairRole(token);

        assertEquals(
                "ROLE_ADMIN",
                role
        );
    }

    @Test
    void deveRetornarTrueQuandoTokenForValido() {

        String token =
                jwtService.gerarToken(
                        "52998224725",
                        "ROLE_ADMIN"
                );

        assertTrue(
                jwtService.tokenValido(token)
        );
    }

    @Test
    void deveRetornarFalseQuandoTokenForInvalido() {

        String token = "token-invalido";

        assertFalse(
                jwtService.tokenValido(token)
        );
    }

    @Test
    void deveRetornarFalseQuandoTokenEstiverExpirado() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1L);

        String token = jwtService.gerarToken("52998224725", "CLIENTE");

        assertFalse(jwtService.tokenValido(token));
    }

    @Test
        void deveGerarTokenComCpfCnpjCorreto() {

        String token =
                jwtService.gerarToken(
                        "12345678901",
                        "ROLE_CLIENTE"
                );

        assertEquals(
                "12345678901",
                jwtService.extrairCpfCnpj(token)
        );
    }

    @Test
    void deveGerarTokenComRoleCorreta() {

        String token =
                jwtService.gerarToken(
                        "12345678901",
                        "ROLE_CLIENTE"
                );

        assertEquals(
                "ROLE_CLIENTE",
                jwtService.extrairRole(token)
        );
    }
}


