package com.example.worldcup.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Integer> {

    Optional<Team> findByName(String name);

    boolean existsByName(String name);

    List<Team> findByNameContainingIgnoreCaseOrderByNameAsc(String fragment);

    /**
     * Loads teams AND their players in one query. LEFT join so teams with no
     * players still appear. This is the version to use.
     *
     * No 'distinct': Hibernate 6 de-duplicates fetched entities itself. Adding it
     * would emit SELECT DISTINCT and make the database do redundant work.
     */
    @Query("select t from Team t left join fetch t.players")
    List<Team> findAllWithPlayers();

    /**
     * The same query with 'distinct', kept only so the demo can show that it
     * changes the SQL but not the result. Advice to add it dates from Hibernate 5.
     */
    @Query("select distinct t from Team t left join fetch t.players")
    List<Team> findAllWithPlayersDistinct();
}
