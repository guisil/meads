package app.meads.identity;

public interface EmailService {

    void sendMagicLink(String recipientEmail);

    void sendPasswordReset(String recipientEmail);

    void sendCredentialsReminder(String recipientEmail);

    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail);

    void sendOrderReviewAlert(String recipientEmail, String competitionName,
                              String jumpsellerOrderId, String customerName);

    void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                    String divisionName, String entrySummary, String entriesUrl);

    void sendCreditNotification(String recipientEmail,
                                int credits, String divisionName,
                                String competitionName, String myEntriesUrl,
                                String contactEmail);
}
