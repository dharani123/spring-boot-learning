package com.example.worldcup.demo;

import com.example.worldcup.game.Game;
import com.example.worldcup.game.GameRepository;
import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 8 - Game: TWO @ManyToOne associations pointing at the SAME entity.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   1. one INSERT with two FK columns, both referencing team
 *   2. join fetch produces TWO joins against team, aliased t1_0 and t2_0
 *   3. "games involving X" needs an OR across both columns - the schema makes
 *      the natural question awkward
 *   4. derived name vs @Query for the same query
 *   5. a gap the schema does NOT close: nothing stops team1 == team2
 *
 * Nothing is deleted. Re-running is safe.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step8SelfJoinDemo implements CommandLineRunner {

    private final GameRepository games;
    private final TeamRepository teams;
    private final TransactionTemplate tx;

    Step8SelfJoinDemo(GameRepository games, TeamRepository teams,
                      PlatformTransactionManager txManager) {
        this.games = games;
        this.teams = teams;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        seed();
        oneRowTwoForeignKeys();
        joinFetchTwice();
        gamesInvolving();
        theGap();
    }

    // ------------------------------------------------------------------- seed
    private void seed() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 1. SEED FIXTURES =====");
            Team argentina = team("Argentina");
            Team brazil    = team("Brazil");
            Team france    = team("France");

            fixture(argentina, brazil);
            fixture(france, argentina);
            fixture(brazil, france);

            System.out.println("games in db: " + games.count());
        });
    }

    // --------------------------------------------- 2. one row, two FK columns
    private void oneRowTwoForeignKeys() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 2. WHAT A GAME ROW LOOKS LIKE =====");
            Game g = games.findAll().get(0);
            System.out.println("game loaded             : " + g);
            System.out.println("  ^ the SELECT read team1_id and team2_id, but NOT the team table");
            System.out.println("team1 class             : " + g.getTeam1().getClass().getSimpleName());
            System.out.println("team2 class             : " + g.getTeam2().getClass().getSimpleName());
            System.out.println("team1 id (free)         : " + g.getTeam1().getId());
            System.out.println("team2 id (free)         : " + g.getTeam2().getId());
            System.out.println("--- now naming them: expect TWO separate selects ---");
            System.out.println(g.getTeam1().getName() + " vs " + g.getTeam2().getName());
        });
    }

    // ------------------------------------------------ 3. two joins, same table
    private void joinFetchTwice() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 3. JOIN FETCH: TWO JOINS AGAINST ONE TABLE =====");
            System.out.println("--- findAllWithTeams() : look at the aliases in the SQL ---");
            List<Game> all = games.findAllWithTeams();
            for (Game g : all) {
                System.out.println("   game " + g.getId() + ": "
                        + g.getTeam1().getName() + " vs " + g.getTeam2().getName());
            }
            System.out.println("one query for " + all.size()
                    + " games and all their teams - no lazy loading at all");
        });
    }

    // ----------------------------------------- 4. the awkward question: OR
    private void gamesInvolving() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 4. \"WHICH GAMES DID ARGENTINA PLAY?\" =====");
            Team argentina = teams.findByName("Argentina").orElseThrow();

            System.out.println("--- derived name: findByTeam1OrTeam2(argentina, argentina) ---");
            System.out.println("    note you must pass the SAME team twice");
            List<Game> viaDerived = games.findByTeam1OrTeam2(argentina, argentina);
            System.out.println("    found " + viaDerived.size() + " games");

            System.out.println("--- @Query: findGamesInvolving(argentina) ---");
            System.out.println("    one parameter, bound twice inside the JPQL");
            List<Game> viaQuery = games.findGamesInvolving(argentina);
            System.out.println("    found " + viaQuery.size() + " games");

            System.out.println("same result, but the second reads like the question being asked.");
            System.out.println("This awkwardness is the schema's doing, not Hibernate's: a game");
            System.out.println("stores its two teams in two columns, so 'either side' means OR.");
        });
    }

    // ------------------------------------------------------ 5. the missing rule
    private void theGap() {
        System.out.println("\n===== 5. A GAP THE SCHEMA DOES NOT CLOSE =====");
        System.out.println("Nothing prevents team1_id == team2_id. Postgres would happily");
        System.out.println("accept a game where Brazil plays Brazil: both FKs are valid.");
        System.out.println("Neither would it stop the same fixture being inserted twice.");
        System.out.println();
        System.out.println("Not demonstrated here on purpose - it would write junk rows.");
        System.out.println("Rules like that are BUSINESS rules. They belong in the service");
        System.out.println("layer (or a CHECK constraint), not in the mapping. This is the");
        System.out.println("first place our CRUD-only scope leaves a visible hole.");
    }

    // ---------------------------------------------------------------- helpers
    private Team team(String name) {
        return teams.findByName(name).orElseGet(() -> teams.save(new Team(name)));
    }

    private void fixture(Team a, Team b) {
        games.findByTeam1AndTeam2(a, b).orElseGet(() -> {
            System.out.println("--- inserting fixture ---");
            return games.save(new Game(a, b));
        });
    }
}
