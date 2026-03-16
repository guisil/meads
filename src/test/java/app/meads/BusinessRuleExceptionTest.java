package app.meads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BusinessRuleExceptionTest {

    @Test
    void shouldCarryMessageKeyAndParams() {
        var ex = new BusinessRuleException("error.entry.limit-subcategory", 3);

        assertThat(ex.getMessageKey()).isEqualTo("error.entry.limit-subcategory");
        assertThat(ex.getParams()).containsExactly(3);
        assertThat(ex.getMessage()).isEqualTo("error.entry.limit-subcategory");
    }

    @Test
    void shouldWorkWithNoParams() {
        var ex = new BusinessRuleException("error.entry.not-found");

        assertThat(ex.getMessageKey()).isEqualTo("error.entry.not-found");
        assertThat(ex.getParams()).isEmpty();
    }

    @Test
    void shouldCarryMultipleParams() {
        var ex = new BusinessRuleException("error.entry.no-credits", 5, 3);

        assertThat(ex.getMessageKey()).isEqualTo("error.entry.no-credits");
        assertThat(ex.getParams()).containsExactly(5, 3);
    }
}
