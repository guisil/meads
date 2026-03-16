package app.meads;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {

    private final String messageKey;
    private final Object[] params;

    public BusinessRuleException(String messageKey, Object... params) {
        super(messageKey);
        this.messageKey = messageKey;
        this.params = params;
    }
}
