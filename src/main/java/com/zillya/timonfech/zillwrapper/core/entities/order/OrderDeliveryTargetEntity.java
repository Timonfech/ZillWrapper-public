package com.zillya.timonfech.zillwrapper.core.entities.order;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.interfaces.IDeliveryTarget;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_delivery_targets")
@Getter
@Setter
@NoArgsConstructor
public class OrderDeliveryTargetEntity implements IDeliveryTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private ContactMethod contactMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false)
    private OutputType outputFormat = OutputType.TEXT;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

}
