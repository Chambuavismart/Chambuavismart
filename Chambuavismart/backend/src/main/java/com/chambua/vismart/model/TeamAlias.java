package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "team_alias", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_alias", columnNames = {"alias", "team_id"})
}, indexes = {
        @Index(name = "idx_alias_alias", columnList = "alias")
})
public class TeamAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String alias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_team_alias_team"))
    private Team team;

    @Column(length = 64)
    private String source;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
