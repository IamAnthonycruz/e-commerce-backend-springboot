package com.pm.authservice.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "username", nullable = false, length = 30, unique = true)
    private String username;
    @Column(name ="password_hash" , nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false )
    private Role role = Role.USER;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
