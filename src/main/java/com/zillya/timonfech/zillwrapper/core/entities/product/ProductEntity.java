package com.zillya.timonfech.zillwrapper.core.entities.product;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.interfaces.IProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity implements IProduct {

    @Id
    @Column(name = "product_id")
    private int productId;

    @Column(name = "brand_id")
    private int brandId;

    @Column(name = "version")
    private int version;

    @Column(name = "group_id")
    private String groupId;


    @Column(name = "regex_pattern")
    private String regexPattern;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "names", columnDefinition = "jsonb")
    private Map<String, String> names;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private Map<String, String> properties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_types", columnDefinition = "jsonb")
    private List<KeyType> keyTypes;


}