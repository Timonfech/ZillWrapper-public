package com.zillya.timonfech.zillwrapper.core.entities.source;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.IEntityType;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Entity
@Table(name = "sources",
                        uniqueConstraints = @UniqueConstraint(
                                name = "uk_source_type_identifier",
                                columnNames = {"type", "identifier_name"}
                        )
)
@Getter
@AllArgsConstructor
public class SourceEntity implements IEntityType, ISource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column()
    private SourceType type;

    @Column()
    private String identifierName;

    public SourceEntity() {

    }

    @Override
    public EntityTypeEnum getEntityType() {
        return EntityTypeEnum.SOURCE;
    }

    @Override
    public Long getSourceId() {
        return id;
    }

    @Override
    public SourceType getSourceType() {
        return this.type;
    }

    @Override
    public String identifierName() {
        return this.identifierName;
    }
}
