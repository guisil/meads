package app.meads.identity.internal;

import app.meads.UserLanguageUpdater;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

@Component
class UserLanguageUpdaterImpl implements UserLanguageUpdater {

    private final UserService userService;

    UserLanguageUpdaterImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void updatePreferredLanguage(String email, String language) {
        var user = userService.findByEmail(email);
        user.updatePreferredLanguage(language);
        userService.updateProfile(user.getId(), user.getName(),
                user.getMeaderyName(), user.getCountry(), language);
    }
}
