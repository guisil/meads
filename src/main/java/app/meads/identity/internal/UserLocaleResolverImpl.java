package app.meads.identity.internal;

import app.meads.BusinessRuleException;
import app.meads.LanguageMapping;
import app.meads.UserLocaleResolver;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class UserLocaleResolverImpl implements UserLocaleResolver {

    private final UserService userService;

    UserLocaleResolverImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Locale resolveLocale(String email) {
        try {
            var user = userService.findByEmail(email);
            return LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
        } catch (BusinessRuleException e) {
            return Locale.ENGLISH;
        }
    }
}
