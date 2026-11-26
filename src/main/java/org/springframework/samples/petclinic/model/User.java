package org.springframework.samples.petclinic.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "enabled")
    private Boolean enabled;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "user", fetch = FetchType.EAGER)
    private Set<Role> roles;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Read-only view of the roles (SpotBugs {@code EI_EXPOSE_REP}): callers can
     * still update each {@link Role}, but the set itself only changes through
     * {@link #setRoles(Set)} and {@link #addRole(String)}. Hibernate uses field
     * access, so the persistent collection is not affected by the view.
     *
     * @return the roles of the user, or {@code null} when none were assigned
     */
    public Set<Role> getRoles() {
        return roles == null ? null : Collections.unmodifiableSet(roles);
    }

    /**
     * Stores a defensive copy of the given roles (SpotBugs
     * {@code EI_EXPOSE_REP2}).
     *
     * @param roles the roles of the user
     */
    public void setRoles(Set<Role> roles) {
        this.roles = roles == null ? null : new HashSet<>(roles);
    }

    @JsonIgnore
    public void addRole(String roleName) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        Role role = new Role();
        role.setName(roleName);
        this.roles.add(role);
    }
}
