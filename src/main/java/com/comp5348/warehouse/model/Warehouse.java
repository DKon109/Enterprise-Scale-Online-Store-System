package com.comp5348.warehouse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * Warehouse entity
 * map to warehouse database
 */

@Entity
@Table(name = "warehouse", uniqueConstraints = {
        @UniqueConstraint(columnNames = "location")
        }
)
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255, unique = true)
    private String location;

    protected Warehouse() {}

    public Warehouse(String location) {
        this.location = location;
    }

    public Warehouse(Long id, String location) {
        this.id = id;
        this.location = location;
    }

    //getter, setter
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }



    public String getLocation() {
        return location;
    }



    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Warehouse)) {
            return false;
        }
        Warehouse that = (Warehouse) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Warehouse{" + "id=" + id + ", location='" + location + "'}";
    }
}