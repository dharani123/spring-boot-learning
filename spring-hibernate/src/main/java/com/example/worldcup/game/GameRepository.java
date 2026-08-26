package com.example.worldcup.game;

import com.example.worldcup.team.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Integer> {

    /** Exact fixture, in the order it was recorded. */
    Optional<Game> findByTeam1AndTeam2(Team team1, Team team2);

    /**
     * "All games this team played" cannot be a single property lookup, because the
     * team could be in either column. Derived queries force you to say it twice.
     */
    List<Game> findByTeam1OrTeam2(Team a, Team b);

    /**
     * Same question, said once. Named parameter is bound twice, which is the main
     * reason to reach for @Query over a derived name here.
     */
    @Query("select g from Game g where g.team1 = :team or g.team2 = :team")
    List<Game> findGamesInvolving(@Param("team") Team team);

    /**
     * Loads both teams up front. Note this produces TWO joins against the same
     * table - Hibernate aliases them apart automatically.
     */
    @Query("select g from Game g join fetch g.team1 join fetch g.team2")
    List<Game> findAllWithTeams();

    /** Every game with its full lineup, in one query. LEFT so empty lineups appear. */
    @Query("select g from Game g left join fetch g.lineup")
    List<Game> findAllWithLineup();

    /**
     * Writes straight into the join table, bypassing the entity model entirely.
     * Exists only so the demo can prove the composite primary key is real and not
     * merely a side effect of using a Java Set. Never do this in real code.
     */
    @Modifying
    @Query(value = "insert into game_player (game_id, player_id) values (:gameId, :playerId)",
           nativeQuery = true)
    void insertLineupRowDirectly(@Param("gameId") Integer gameId,
                                 @Param("playerId") Integer playerId);
}
