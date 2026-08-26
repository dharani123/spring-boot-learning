package com.example.worldcup.demo;

import com.example.worldcup.game.Game;
import com.example.worldcup.game.GameRepository;
import com.example.worldcup.player.Player;
import com.example.worldcup.player.PlayerRepository;
import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 11 - @ManyToMany: which players appeared in which game.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   1. Flyway applied V2 on top of V1 - check the startup log
 *   2. no FK column on either entity; the relationship lives in game_player
 *   3. adding to the lineup writes a join-table INSERT (not an UPDATE)
 *   4. the inverse side (player.games) still writes nothing
 *   5. removing writes a targeted DELETE - because the collection is a Set
 *   6. the composite PK rejects the same player twice in one game
 *   7. why this mapping dies the moment the relationship needs its own data
 *
 * Nothing is deleted permanently. Re-running is safe.
 */
@Component   // <-- active demo; comment this out when moving on
class Step11ManyToManyDemo implements CommandLineRunner {

    private final GameRepository games;
    private final PlayerRepository players;
    private final TeamRepository teams;
    private final TransactionTemplate tx;

    Step11ManyToManyDemo(GameRepository games, PlayerRepository players,
                         TeamRepository teams, PlatformTransactionManager txManager) {
        this.games = games;
        this.players = players;
        this.teams = teams;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        noFkColumns();
        buildLineup();
        inverseStillWritesNothing();
        removeIsTargeted();
        duplicateRejected();
        querying();
        whenManyToManyBreaks();
    }

    // ------------------------------------------------------ 1. where it lives
    private void noFkColumns() {
        System.out.println("\n===== 1. NEITHER TABLE GAINED A COLUMN =====");
        System.out.println("game still has: id, team1_id, team2_id");
        System.out.println("player still has: id, name, team_id, role_id");
        System.out.println("The relationship lives entirely in the game_player join table,");
        System.out.println("created by V2. Scroll up: Flyway reported 'Migrating schema to");
        System.out.println("version 2' the first time you ran this, then went quiet.");
        System.out.println("V1 was never re-run - that is the point of the history table.");
    }

    // ------------------------------------------------------- 2. build a lineup
    private void buildLineup() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 2. ADDING TO THE OWNING SIDE (game.lineup) =====");
            Game game1 = firstGame();
            System.out.println("game " + game1.getId() + ": "
                    + game1.getTeam1().getName() + " vs " + game1.getTeam2().getName());

            System.out.println("lineup class      : " + game1.lineupMutableForDemo().getClass().getName());
            System.out.println("initialized?      : "
                    + Hibernate.isInitialized(game1.lineupMutableForDemo()));
            System.out.println("--- touching it: expect a SELECT against game_player ---");
            System.out.println("current size      : " + game1.lineupMutableForDemo().size());

            List<Player> squad = squadFor(game1);
            System.out.println("--- adding " + squad.size() + " players via addToLineup() ---");
            for (Player p : squad) {
                if (!game1.lineupMutableForDemo().contains(p)) {
                    game1.addToLineup(p);
                }
            }
            System.out.println("    ^ notice one SELECT per player above: addToLineup() also");
            System.out.println("      touches player.games, which forces each player's inverse");
            System.out.println("      collection to load. Keeping both sides in sync is not free.");
            System.out.println("--- committing: expect INSERTs into game_player, no UPDATEs ---");
        });
        tx.executeWithoutResult(s ->
                System.out.println("lineup size now   : " + firstGame().getLineup().size()));
    }

    // -------------------------------------------- 3. inverse side writes nothing
    private void inverseStillWritesNothing() {
        System.out.println("\n===== 3. THE INVERSE SIDE (player.games) STILL WRITES NOTHING =====");
        Integer gameId = tx.execute(s -> games.findAll().get(1).getId());
        Integer playerId = tx.execute(s -> players.findByName("Lionel Messi").orElseThrow().getId());
        int before = countAppearances(playerId);

        tx.executeWithoutResult(status -> {
            Player messi = players.findById(playerId).orElseThrow();
            Game other = games.findById(gameId).orElseThrow();
            System.out.println("--- messi.gamesMutableForDemo().add(game " + gameId + ") ---");
            messi.gamesMutableForDemo().add(other);
            System.out.println("--- committing: expect NO insert ---");
        });

        int after = countAppearances(playerId);
        System.out.println("Messi appearances before : " + before);
        System.out.println("Messi appearances after  : " + after);
        System.out.println(before == after
                ? "  UNCHANGED. mappedBy = 'I am the view, not the writer.'"
                : "  !! changed - unexpected");
    }

    // ---------------------------------------------- 4. remove -> targeted DELETE
    private void removeIsTargeted() {
        System.out.println("\n===== 4. REMOVING FROM A Set -> ONE TARGETED DELETE =====");
        Integer[] ids = tx.execute(s -> {
            Game g = firstGame();
            Player victim = g.getLineup().iterator().next();
            return new Integer[]{g.getId(), victim.getId()};
        });

        tx.executeWithoutResult(status -> {
            Game g = games.findById(ids[0]).orElseThrow();
            Player victim = players.findById(ids[1]).orElseThrow();
            System.out.println("--- removeFromLineup(player " + ids[1] + ") ---");
            g.removeFromLineup(victim);
            System.out.println("--- committing: ONE delete, with both ids in the where clause ---");
        });
        System.out.println("  ^ With a List instead of a Set, Hibernate would DELETE every row");
        System.out.println("    for this game and re-INSERT the survivors. Use Set for @ManyToMany.");

        // put it back so the demo is re-runnable
        tx.executeWithoutResult(status -> {
            Game g = games.findById(ids[0]).orElseThrow();
            Player victim = players.findById(ids[1]).orElseThrow();
            g.addToLineup(victim);
        });
        System.out.println("  (added back, so re-running this demo is safe)");
    }

    // ------------------------------------------------ 5. composite PK protects us
    private void duplicateRejected() {
        System.out.println("\n===== 5. THE COMPOSITE PRIMARY KEY REJECTS DUPLICATES =====");
        System.out.println("A Set already prevents this in Java. Forcing it at the SQL level:");
        Integer[] ids = tx.execute(s -> {
            Game g = firstGame();
            return new Integer[]{g.getId(), g.getLineup().iterator().next().getId()};
        });
        try {
            tx.executeWithoutResult(status ->
                    games.insertLineupRowDirectly(ids[0], ids[1]));
            System.out.println("!! accepted - unexpected");
        } catch (DataIntegrityViolationException e) {
            System.out.println("REJECTED: " + e.getClass().getSimpleName());
            System.out.println("  ^ primary key (game_id, player_id) - a player cannot appear");
            System.out.println("    twice in the same game, whatever writes to the table.");
        }
    }

    // ------------------------------------------------------------ 6. querying
    private void querying() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 6. QUERYING ACROSS THE JOIN TABLE =====");
            Game g = firstGame();
            System.out.println("--- game.getLineup() : join through game_player ---");
            for (Player p : g.getLineup()) {
                System.out.println("   " + p.getName());
            }

            Player messi = players.findByName("Lionel Messi").orElseThrow();
            System.out.println("--- messi.getGames() : the SAME join table, other direction ---");
            System.out.println("   appearances: " + messi.getGames().size());

            System.out.println("--- games.findAllWithLineup() : one query, no N+1 ---");
            for (Game each : games.findAllWithLineup()) {
                System.out.println("   game " + each.getId()
                        + " -> " + each.getLineup().size() + " players");
            }
        });
    }

    // ------------------------------------------- 7. the limit of @ManyToMany
    private void whenManyToManyBreaks() {
        System.out.println("\n===== 7. WHEN @ManyToMany STOPS BEING ENOUGH =====");
        System.out.println("game_player holds two FKs and nothing else. That is the ONLY shape");
        System.out.println("@ManyToMany can express.");
        System.out.println();
        System.out.println("The moment the relationship needs its own data - minutes played,");
        System.out.println("goals scored, started or substitute, shirt number for this game -");
        System.out.println("there is nowhere to put it. @ManyToMany has no room for attributes.");
        System.out.println();
        System.out.println("The fix is to promote the relationship to an entity:");
        System.out.println("    Appearance { @ManyToOne Game game;  @ManyToOne Player player;");
        System.out.println("                 int minutesPlayed;  int goals; }");
        System.out.println("and replace the @ManyToMany with two @OneToMany-to-Appearance sides.");
        System.out.println();
        System.out.println("Real systems reach that point surprisingly often, which is why many");
        System.out.println("teams skip @ManyToMany and model the join table as an entity up front.");
    }

    // ---------------------------------------------------------------- helpers
    private Game firstGame() {
        return games.findAll().stream()
                .min((a, b) -> Integer.compare(a.getId(), b.getId())).orElseThrow();
    }

    private List<Player> squadFor(Game game) {
        Team t1 = game.getTeam1();
        Team t2 = game.getTeam2();
        return players.findAll().stream()
                .filter(p -> p.getTeam().getId().equals(t1.getId())
                          || p.getTeam().getId().equals(t2.getId()))
                .toList();
    }

    private int countAppearances(Integer playerId) {
        return tx.execute(s -> players.findById(playerId).orElseThrow().getGames().size());
    }
}
