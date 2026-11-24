package in.medmate.pharmacy_server.identity.mail;

public interface MailSender {

    void send(String to, String subject, String text);
}
