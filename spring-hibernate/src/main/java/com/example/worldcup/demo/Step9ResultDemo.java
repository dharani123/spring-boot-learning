package com.example.worldcup.demo;

import com.example.worldcup.game.Game;
import com.example.worldcup.game.GameRepository;
import com.example.worldcup.player.Player;
import com.example.worldcup.player.PlayerRepository;
import com.example.worldcup.result.Result;
import com.example.worldcup.result.ResultRepository;
import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 9 - Result: four foreign keys across three tables.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   1. ONE insert carrying four FK columns
 *   2. join fetch -> four joins, two of them against team under different aliases
 *   3. the UNIQUE constraint on game_id rejects a second result for the same game
 *      (this is the 409 Conflict case our REST layer will need to handle)
 *   4. an aggregate query returning Object[] rows instead of entities
 *   5. the business rules NOTHING in this model enforces
 *
 * Nothing is deleted. Re-running is safe.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step9ResultDemo implements CommandLineRunner {

    private final ResultRepository results;
    private final GameRepository games;
    private final TeamRepository teams;
    private final PlayerRepository players;
    private final TransactionTemplate tx;

    Step9ResultDemo(ResultRepository results, GameRepository games, TeamRepository teams,
                    PlayerRepository players, PlatformTransactionManager txManager) {
        this.results = results;
        this.games = games;
        this.teams = teams;
        this.players = players;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        seed();
        fourForeignKeys();
        joinFetchFour();
        uniqueConstraint();
        aggregate();
        whatIsNotEnforced();
    }

    // ------------------------------------------------------------------- seed
    private void seed() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 1. RECORD THE RESULTS =====");
            Team argentina = teams.findByName("Argentina").orElseThrow();
            Team brazil    = teams.findByName("Brazil").orElseThrow();
            Team france    = teams.findByName("France").orElseThrow();

            Player messi   = players.findByName("Lionel Messi").orElseThrow();
            Player neymar  = players.findByName("Neymar Jr").orElseThrow();
            Player mbappe  = players.findByName("Kylian Mbappe").orElseThrow();

            // Argentina vs Brazil -> Argentina win, Messi MOTM
            record_(games.findByTeam1AndTeam2(argentina, brazil).orElseThrow(),
                    argentina, brazil, messi);
            // France vs Argentina -> Argentina win, Messi MOTM again
            record_(games.findByTeam1AndTeam2(france, argentina).orElseThrow(),
                    argentina, france, messi);
            // Brazil vs France -> Brazil win, Neymar MOTM
            record_(games.findByTeam1AndTeam2(brazil, france).orElseThrow(),
                    brazil, france, neymar);

            System.out.println("results in db: " + results.count()
                    + "   (mbappe=" + mbappe.getId() + " has no award yet)");
        });
    }

    // ---------------------------------------------- 2. one row, four FK columns
    private void fourForeignKeys() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 2. WHAT A RESULT ROW LOOKS LIKE =====");
            Result r = results.findAll().get(0);
            System.out.println("result loaded        : " + r);
            System.out.println("  ^ ONE select on result. No game, team or player was read.");
            System.out.println("game               : " + r.getGame().getClass().getSimpleName()
                    + "  id=" + r.getGame().getId());
            System.out.println("winningTeam        : " + r.getWinningTeam().getClass().getSimpleName()
                    + "  id=" + r.getWinningTeam().getId());
            System.out.println("losingTeam         : " + r.getLosingTeam().getClass().getSimpleName()
                    + "  id=" + r.getLosingTeam().getId());
            System.out.println("playerOfTheMatch   : " + r.getPlayerOfTheMatch().getClass().getSimpleName()
                    + "  id=" + r.getPlayerOfTheMatch().getId());
            System.out.println("--- four ids, still zero extra queries. Now naming them: ---");
            System.out.println("   " + r.getWinningTeam().getName()
                    + " beat " + r.getLosingTeam().getName()
                    + ", MOTM " + r.getPlayerOfTheMatch().getName());
            System.out.println("   ^ that cost THREE lazy selects. Four with the game.");
        });
    }

    // ------------------------------------------------- 3. join fetch, four deep
    private void joinFetchFour() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 3. JOIN FETCH ALL FOUR =====");
            System.out.println("--- findAllDetailed() : count the joins, note team appears twice ---");
            List<Result> all = results.findAllDetailed();
            for (Result r : all) {
                System.out.println("   game " + r.getGame().getId() + ": "
                        + r.getWinningTeam().getName() + " beat " + r.getLosingTeam().getName()
                        + "  |  MOTM " + r.getPlayerOfTheMatch().getName());
            }
            System.out.println("one query for " + all.size() + " results and all their references");
        });
    }

    // ------------------------------------------------- 4. the unique constraint
    private void uniqueConstraint() {
        System.out.println("\n===== 4. UNIQUE game_id : ONE RESULT PER GAME =====");
        System.out.println("trying to record a SECOND result for a game that already has one");
        try {
            tx.executeWithoutResult(status -> {
                Result existing = results.findAll().get(0);
                Game alreadyDecided = existing.getGame();
                Team someTeam = teams.findByName("France").orElseThrow();
                Team other    = teams.findByName("Brazil").orElseThrow();
                Player anyone = players.findByName("Kylian Mbappe").orElseThrow();

                results.save(new Result(alreadyDecided, someTeam, other, anyone));
            });
            System.out.println("!! it worked - unexpected");
        } catch (DataIntegrityViolationException e) {
            System.out.println("REJECTED by the database:");
            System.out.println("  spring exception : " + e.getClass().getSimpleName());
            System.out.println("  root cause       : " + rootCause(e).getMessage().lines().findFirst().orElse(""));
            System.out.println();
            System.out.println("  ^ Spring translated the raw PSQLException into");
            System.out.println("    DataIntegrityViolationException - a portable exception that");
            System.out.println("    means the same thing on MySQL or Oracle.");
            System.out.println("    In the REST layer this becomes 409 CONFLICT, not 500.");
        }
        System.out.println("results still in db: " + results.count() + "   <- rolled back");
    }

    // --------------------------------------------------- 5. aggregate projection
    private void aggregate() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 5. AN AGGREGATE QUERY (not entities) =====");
            System.out.println("--- countAwardsPerPlayer() : group by, returns Object[] ---");
            for (Object[] row : results.countAwardsPerPlayer()) {
                System.out.println("   " + row[0] + " : " + row[1] + " award(s)");
            }
            System.out.println("Rows here are NOT managed entities - no dirty checking, no");
            System.out.println("lazy loading. Just values. Note the JPQL walked");
            System.out.println("r.playerOfTheMatch.name in one step: an IMPLICIT join.");
        });
    }

    // ------------------------------------------------ 6. what is NOT enforced
    private void whatIsNotEnforced() {
        System.out.println("\n===== 6. WHAT THIS MODEL DOES NOT ENFORCE =====");
        System.out.println("All of the following would be accepted by both Hibernate and Postgres:");
        System.out.println("  - a winning team that never played in that game");
        System.out.println("  - winning_team_id == losing_team_id");
        System.out.println("  - a player of the match who plays for a third, uninvolved team");
        System.out.println("  - a game whose team1_id == team2_id (from step 8)");
        System.out.println();
        System.out.println("Every FK is valid in isolation; the COMBINATION is nonsense.");
        System.out.println("Foreign keys check existence, never coherence.");
        System.out.println();
        System.out.println("Not demonstrated - it would write junk rows into your database.");
        System.out.println("These are business rules. They belong in a service method that");
        System.out.println("loads the game and checks the teams before saving.");
    }

    // ---------------------------------------------------------------- helpers
    private void record_(Game game, Team winner, Team loser, Player motm) {
        results.findByGame(game).orElseGet(() -> {
            System.out.println("--- inserting result for game " + game.getId() + " ---");
            return results.save(new Result(game, winner, loser, motm));
        });
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
