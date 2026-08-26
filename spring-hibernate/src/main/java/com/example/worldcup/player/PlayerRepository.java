package com.example.worldcup.player;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    Optional<Player> findByName(String name);

    /** Traverses the association: player -> team -> name. Generates a JOIN. */
    List<Player> findByTeamName(String teamName);

    /**
     * Reads as "player -> team -> id", so Spring Data emits a JOIN even though
     * player.team_id already holds the value. Harmless here, but worth seeing:
     * the method name decides the SQL, and the obvious name is not always the
     * cheapest query.
     */
    List<Player> findByTeamId(Integer teamId);

    /** Loads players AND their teams and roles in ONE query - the N+1 cure. */
    @Query("select p from Player p join fetch p.team join fetch p.role")
    List<Player> findAllWithTeamAndRole();
}
