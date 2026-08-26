package com.example.worldcup.team;

import com.example.worldcup.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The INVERSE side of Player.team. Read carefully:
     *
     *   mappedBy = "team"   names the FIELD in Player that owns this relationship.
     *                       It is a Java field name, NOT the team_id column.
     *
     * "Inverse" means this collection owns NOTHING. Adding a Player here does not
     * write anything to the database - only setting player.team does. See
     * Step10BidirectionalDemo section 3.
     *
     * No cascade and no orphanRemoval on purpose: deleting a team must not delete
     * its players, and dropping a player from this list must not delete the row.
     */
    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    private List<Player> players = new ArrayList<>();

    protected Team() {
        // required by JPA
    }

    public Team(String name) {
        this.name = name;
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

    /** Unmodifiable, so callers cannot add to it and expect a database write. */
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /**
     * Keeps BOTH sides in sync. The second line is the one that persists;
     * the first only keeps this in-memory object graph honest.
     */
    public void addPlayer(Player player) {
        players.add(player);
        player.setTeam(this);
    }

    /**
     * The raw, MUTABLE list. Exists only so Step10BidirectionalDemo can show what
     * happens when you add to the inverse side without setting player.team.
     * Do not use this in real code - use addPlayer(), which syncs both sides.
     */
    public List<Player> playersMutableForDemo() {
        return players;
    }

    @Override
    public String toString() {
        return "Team{id=" + id + ", name='" + name + "'}";
    }
}
