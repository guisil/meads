package app.meads.identity;

public interface AccessCodeValidator {

    boolean validate(String email, String code);
}
