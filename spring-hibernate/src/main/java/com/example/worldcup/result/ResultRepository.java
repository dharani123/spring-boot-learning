package com.example.worldcup.result;

import com.example.worldcup.game.Game;
import com.example.worldcup.player.Player;
import com.example.worldcup.team.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResultRepository extends JpaRepository<Result, Integer> {

    /** Optional, not List - game_id is unique, so there is at most one. */
    Optional<Result> findByGame(Game game);

    boolean existsByGame(Game game);

    List<Result> findByWinningTeam(Team team);

    List<Result> findByPlayerOfTheMatch(Player player);

    /**
     * All four associations in one query. Produces four joins, two of which hit
     * the team table under different aliases.
     */
    @Query("""
            select r from Result r
            join fetch r.game
            join fetch r.winningTeam
            join fetch r.losingTeam
            join fetch r.playerOfTheMatch
            """)
    List<Result> findAllDetailed();

    /** How many player-of-the-match awards each player has won. */
    @Query("""
            select r.playerOfTheMatch.name, count(r)
            from Result r
            group by r.playerOfTheMatch.name
            order by count(r) desc, r.playerOfTheMatch.name
            """)
    List<Object[]> countAwardsPerPlayer();
}
