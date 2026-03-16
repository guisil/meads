package app.meads.identity;

import java.util.Locale;

public interface EmailService {

    void sendMagicLink(String recipientEmail, Locale locale);

    void sendPasswordReset(String recipientEmail, Locale locale);

    void sendCredentialsReminder(String recipientEmail, Locale locale);

    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail);

    void sendOrderReviewAlert(String recipientEmail, String competitionName,
                              String jumpsellerOrderId, String customerName,
                              String divisionNames);

    void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                    String divisionName, java.util.List<String> entryLines,
                                    String entriesUrl, Locale locale);

    void sendCreditNotification(String recipientEmail,
                                int credits, String divisionName,
                                String competitionName, String myEntriesUrl,
                                String contactEmail, Locale locale);
}
