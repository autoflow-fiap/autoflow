package com.autoflow.infrastructure.persistence.entity.usuario;

import com.autoflow.domain.usuario.RoleEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String cpfCnpj;

    private String senha;

    @Enumerated(EnumType.STRING)
    private RoleEnum role;
}
