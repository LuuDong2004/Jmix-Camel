package com.vn.jmixcamel.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "CUSTOMER", indexes = {
        @Index(name = "IDX_CUSTOMER__ON_NAME_PHONE", columnList = "NAME, PHONE")
})
public class Customer {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "SEGMENT")
    private String segment;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @InstanceName
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }
}
