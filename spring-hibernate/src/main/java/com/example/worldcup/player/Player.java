package com.example.worldcup.player;

import com.example.worldcup.game.Game;
import com.example.worldcup.role.Role;
import com.example.worldcup.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * MANY players belong to ONE team.
     *
     * This side owns the relationship: it holds the team_id foreign key column,
     * so changing this field is what writes to the database.
     *
     * LAZY means the Team is not loaded with the Player. Calling getTeam()
     * returns a proxy; the SELECT fires only when a method on it is called,
     * and only if a transaction is still open.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * The INVERSE side of Game.lineup. mappedBy names the FIELD in Game.
     * Adding here writes nothing - same rule as Team.players.
     */
    @ManyToMany(mappedBy = "lineup", fetch = FetchType.LAZY)
    private Set<Game> games = new HashSet<>();

    protected Player() {
        // required by JPA
    }

    public Player(String name, Team team, Role role) {
        this.name = name;
        this.team = team;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Set<Game> getGames() {
        return Collections.unmodifiableSet(games);
    }

    /** Raw mutable set, used by Game's sync helpers and by the demo. */
    public Set<Game> gamesMutableForDemo() {
        return games;
    }

    /**
     * Deliberately does NOT touch team, role or games - toString() is called by loggers
     * and debuggers at arbitrary times, and touching a lazy association here
     * would fire surprise queries or throw LazyInitializationException.
     */
    @Override
    public String toString() {
        return "Player{id=" + id + ", name='" + name + "'}";
    }
}
