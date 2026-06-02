package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ContactMethod> contacts;

    @Column(name = "name", length = 120)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(length = 20)
    private Locale locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", length = 20)
    private ClientType clientType = ClientType.STANDARD;
}
