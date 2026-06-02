package com.zillya.timonfech.zillwrapper.core.entities.license;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
public class DinoKeyEntity extends BaseKeyEntity {
    @OneToMany(mappedBy = "dinoKey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LicenseActivationEntity> activations = new java.util.ArrayList<>();
}