package app.meads.judging.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "score_fields")
@Getter
public class ScoreField {

    @Id
    private UUID id;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "max_value", nullable = false)
    private int maxValue;

    @Column(name = "value")
    private Integer value;

    @Column(name = "comment", length = 2000)
    private String comment;

    protected ScoreField() {
    }

    public ScoreField(String fieldName, int maxValue) {
        this.id = UUID.randomUUID();
        this.fieldName = fieldName;
        this.maxValue = maxValue;
    }

    public void update(Integer value, String comment) {
        if (value != null && (value < 0 || value > maxValue)) {
            throw new IllegalArgumentException(
                    "Value " + value + " out of range for field " + fieldName + " (max " + maxValue + ")");
        }
        this.value = value;
        this.comment = comment;
    }
}
