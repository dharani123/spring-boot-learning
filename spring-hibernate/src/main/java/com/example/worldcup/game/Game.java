package com.example.worldcup.game;

import com.example.worldcup.player.Player;
import com.example.worldcup.team.Team;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A fixture between two teams.
 *
 * The interesting part: TWO @ManyToOne associations pointing at the SAME entity.
 * Nothing distinguishes them except the @JoinColumn name, so the annotation stops
 * being optional here - it is the only thing telling Hibernate which field owns
 * which column.
 */
@Entity
@Table(name = "game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team1_id", nullable = false)
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team2_id", nullable = false)
    private Team team2;

    /**
     * The players who appeared in this game - the OWNING side of the many-to-many.
     *
     * "Owning" here means: this side's changes are what Hibernate writes into the
     * game_player join table. Player.games is the inverse view and writes nothing.
     *
     *   joinColumns         -> the FK pointing back at THIS entity (game)
     *   inverseJoinColumns  -> the FK pointing at the OTHER entity (player)
     *
     * Set, not List. With a List, Hibernate deletes every join row and re-inserts
     * them all whenever the collection changes; with a Set it issues a single
     * targeted insert or delete. See the demo.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "game_player",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id"))
    private Set<Player> lineup = new HashSet<>();

    protected Game() {
        // required by JPA
    }

    public Game(Team team1, Team team2) {
        this.team1 = team1;
        this.team2 = team2;
    }

    public Integer getId() {
        return id;
    }

    public Team getTeam1() {
        return team1;
    }

    public void setTeam1(Team team1) {
        this.team1 = team1;
    }

    public Team getTeam2() {
        return team2;
    }

    public void setTeam2(Team team2) {
        this.team2 = team2;
    }

    public Set<Player> getLineup() {
        return Collections.unmodifiableSet(lineup);
    }

    /** Keeps both sides in sync. Only this side writes to game_player. */
    public void addToLineup(Player player) {
        lineup.add(player);
        player.gamesMutableForDemo().add(this);
    }

    public void removeFromLineup(Player player) {
        lineup.remove(player);
        player.gamesMutableForDemo().remove(this);
    }

    /** Raw mutable set, for the demo only. */
    public Set<Player> lineupMutableForDemo() {
        return lineup;
    }

    /** Does not touch team1 / team2 / lineup - all lazy. */
    @Override
    public String toString() {
        return "Game{id=" + id + "}";
    }
}
