package me.hsgamer.testgenesis.cms.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class TestEntity extends PanacheEntity {
    public String name;
    public String description;
    public String testType;

    @ManyToMany
    @JoinTable(
            name = "test_payloads",
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "payload_id")
    )
    public List<PayloadEntity> payloads = new ArrayList<>();
}
