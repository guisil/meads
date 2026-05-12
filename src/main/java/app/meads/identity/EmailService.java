package app.meads.identity;

import java.util.Locale;

public interface EmailService {

    enum ResultsAnnouncementType {
        INITIAL_NO_CUSTOM,
        REPUBLISH_NO_CUSTOM,
        CUSTOM_MESSAGE
    }

    void sendResultsAnnouncement(String recipientEmail, Locale locale,
                                  ResultsAnnouncementType type,
                                  String competitionName, String divisionName,
                                  String customOrJustificationBody,
                                  String resultsUrl, String contactEmail);

    void sendMagicLink(String recipientEmail, Locale locale);

    void sendPasswordReset(String recipientEmail, Locale locale);

    void sendCredentialsReminder(String recipientEmail, Locale locale);

    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail, Locale locale);

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
