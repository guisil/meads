package app.meads.identity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "meadery_name")
    private String meaderyName;

    @Column(length = 2)
    private String country;

    private Instant updatedAt;

    protected User() {} // JPA

    public User(String email, String name, UserStatus status, Role role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.name = name;
        this.status = status;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void assignPasswordHash(String encodedPasswordHash) {
        this.passwordHash = encodedPasswordHash;
    }

    public void updateMeaderyName(String meaderyName) {
        this.meaderyName = meaderyName;
    }

    public void updateCountry(String country) {
        this.country = country;
    }

    public void updateDetails(String name, Role role, UserStatus status) {
        this.name = name;
        this.role = role;
        this.status = status;
    }
}
