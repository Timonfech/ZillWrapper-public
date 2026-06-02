package com.zillya.timonfech.zillwrapper.core.entities.license;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
public class WhiteAdminKeyEntity extends BaseKeyEntity {
    Integer reservedServers;
    String company;

    @OneToMany(mappedBy = "whiteAdminKey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhiteAdminActivationEntity> activations = new ArrayList<>();

}
