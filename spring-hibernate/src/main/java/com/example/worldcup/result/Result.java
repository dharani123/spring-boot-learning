package com.example.worldcup.result;

import com.example.worldcup.game.Game;
import com.example.worldcup.player.Player;
import com.example.worldcup.team.Team;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * The outcome of a game. The densest mapping in the project:
 * four foreign keys across three tables.
 *
 *   game_id                -> game    (UNIQUE, so this one is @OneToOne)
 *   winning_team_id        -> team
 *   losing_team_id         -> team    (same table again)
 *   player_of_the_match_id -> player
 *
 * Note what this class does NOT enforce - see Step9ResultDemo section 5.
 */
@Entity
@Table(name = "result")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * @OneToOne rather than @ManyToOne because result.game_id carries a UNIQUE
     * constraint: a game has at most one result. Hibernate would accept
     * @ManyToOne here and generate identical SQL, but @OneToOne states the rule.
     *
     * This is the OWNING side (it holds the FK column), which is why LAZY works.
     * On the INVERSE side of a @OneToOne, lazy loading silently does not work -
     * see the demo.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, unique = true)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winning_team_id", nullable = false)
    private Team winningTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "losing_team_id", nullable = false)
    private Team losingTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_of_the_match_id", nullable = false)
    private Player playerOfTheMatch;

    protected Result() {
        // required by JPA
    }

    public Result(Game game, Team winningTeam, Team losingTeam, Player playerOfTheMatch) {
        this.game = game;
        this.winningTeam = winningTeam;
        this.losingTeam = losingTeam;
        this.playerOfTheMatch = playerOfTheMatch;
    }

    public Integer getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Team getWinningTeam() {
        return winningTeam;
    }

    public void setWinningTeam(Team winningTeam) {
        this.winningTeam = winningTeam;
    }

    public Team getLosingTeam() {
        return losingTeam;
    }

    public void setLosingTeam(Team losingTeam) {
        this.losingTeam = losingTeam;
    }

    public Player getPlayerOfTheMatch() {
        return playerOfTheMatch;
    }

    public void setPlayerOfTheMatch(Player playerOfTheMatch) {
        this.playerOfTheMatch = playerOfTheMatch;
    }

    /** Touches none of the four lazy associations. */
    @Override
    public String toString() {
        return "Result{id=" + id + "}";
    }
}
