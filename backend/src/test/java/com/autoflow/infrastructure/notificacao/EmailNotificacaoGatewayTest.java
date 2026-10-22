package com.autoflow.infrastructure.notificacao;

import com.autoflow.application.input.notificacao.MensagemNotificacao;
import com.autoflow.infrastructure.observability.NewRelicBusinessTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificacaoGatewayTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NewRelicBusinessTelemetry telemetry;

    @InjectMocks
    private EmailNotificacaoGateway service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "remetente", "noreply@autoflow.com");
    }

    @Test
    void deveEnviarEmailComDadosCorretos() {
        MensagemNotificacao mensagem = new MensagemNotificacao(
                "cliente@email.com", "Orçamento disponível", "Seu orçamento foi gerado."
        );

        service.enviar(mensagem);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage emailEnviado = captor.getValue();
        assertEquals("noreply@autoflow.com", emailEnviado.getFrom());
        assertArrayEquals(new String[]{"cliente@email.com"}, emailEnviado.getTo());
        assertEquals("Orçamento disponível", emailEnviado.getSubject());
        assertEquals("Seu orçamento foi gerado.", emailEnviado.getText());
    }

    @Test
    void deveChamarMailSenderUmaVez() {
        MensagemNotificacao mensagem = new MensagemNotificacao(
                "destino@email.com", "Assunto", "Corpo"
        );

        service.enviar(mensagem);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void deveRegistrarFalhaDeIntegracaoSemSuprimirErroDoEnvio() {
        MailSendException failure = new MailSendException("synthetic failure");
        doThrow(failure).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> service.enviar(
                new MensagemNotificacao("synthetic@example.invalid", "assunto sintético", "corpo sintético")));

        verify(telemetry).recordIntegrationFailure("email", "SEND_NOTIFICATION", failure);
    }
}
