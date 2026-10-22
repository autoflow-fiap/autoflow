package com.autoflow.infrastructure.notificacao;

import com.autoflow.application.gateway.NotificacaoGateway;
import com.autoflow.application.input.notificacao.MensagemNotificacao;
import com.autoflow.infrastructure.observability.NewRelicBusinessTelemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificacaoGateway implements NotificacaoGateway {

    private final JavaMailSender mailSender;
    private final NewRelicBusinessTelemetry telemetry;

    public EmailNotificacaoGateway(JavaMailSender mailSender, NewRelicBusinessTelemetry telemetry) {
        this.mailSender = mailSender;
        this.telemetry = telemetry;
    }

    @Value("${spring.mail.username}")
    private String remetente;

    @Override
    public void enviar(MensagemNotificacao mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(remetente);
        email.setTo(mensagem.destinatario());
        email.setSubject(mensagem.assunto());
        email.setText(mensagem.corpo());
        try {
            mailSender.send(email);
        } catch (RuntimeException exception) {
            telemetry.recordIntegrationFailure("email", "SEND_NOTIFICATION", exception);
            throw exception;
        }
    }
}
