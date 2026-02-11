package app.meads.identity.internal;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}
