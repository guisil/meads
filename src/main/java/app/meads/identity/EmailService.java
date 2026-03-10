package app.meads.identity;

public interface EmailService {

    void sendMagicLink(String recipientEmail);

    void sendPasswordReset(String recipientEmail);

    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail);
}
