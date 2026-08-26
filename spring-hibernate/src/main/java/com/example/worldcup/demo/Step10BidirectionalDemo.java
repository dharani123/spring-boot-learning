package com.example.worldcup.demo;

import com.example.worldcup.player.Player;
import com.example.worldcup.player.PlayerRepository;
import com.example.worldcup.role.Role;
import com.example.worldcup.role.RoleRepository;
import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import java.util.List;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 10 - the INVERSE side: Team.players with mappedBy.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   1. @OneToMany(mappedBy=...) adds NO column - validate still passes
 *   2. the collection is a PersistentBag proxy, loaded on first touch
 *   3. THE BIG ONE: adding to team.players persists NOTHING.
 *      Only player.setTeam(...) writes, because Player owns the FK.
 *   4. keeping both sides in sync is YOUR job - addPlayer() does it
 *   5. touching the collection outside a transaction throws
 *   6. join fetch on a COLLECTION - and why 'distinct' is obsolete advice
 *
 * The "Loan Player" row flips between two teams on each run, so re-running is
 * safe and always shows a real UPDATE.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step10BidirectionalDemo implements CommandLineRunner {

    private final TeamRepository teams;
    private final PlayerRepository players;
    private final RoleRepository roles;
    private final TransactionTemplate tx;

    Step10BidirectionalDemo(TeamRepository teams, PlayerRepository players,
                            RoleRepository roles, PlatformTransactionManager txManager) {
        this.teams = teams;
        this.players = players;
        this.roles = roles;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        seed();
        noSchemaChange();
        collectionIsLazy();
        inverseSideWritesNothing();
        owningSideWrites();
        outsideTransaction();
        joinFetchCollection();
    }

    // ------------------------------------------------------------------- seed
    private void seed() {
        tx.executeWithoutResult(status -> {
            Role forward = roles.findByName("Forward").orElseThrow();
            Team brazil = teams.findByName("Brazil").orElseThrow();
            players.findByName("Loan Player")
                    .orElseGet(() -> players.save(new Player("Loan Player", brazil, forward)));
        });
    }

    // -------------------------------------------------------- 1. no new column
    private void noSchemaChange() {
        System.out.println("\n===== 1. @OneToMany(mappedBy) ADDS NO COLUMN =====");
        System.out.println("The app started, so ddl-auto=validate is happy.");
        System.out.println("No migration was needed: the relationship was ALREADY in the");
        System.out.println("database as player.team_id. mappedBy just exposes it from the");
        System.out.println("other direction. An inverse side is a Java-side view, not storage.");
    }

    // ------------------------------------------------- 2. the collection proxy
    private void collectionIsLazy() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 2. THE COLLECTION IS A LAZY PROXY =====");
            Team argentina = teams.findByName("Argentina").orElseThrow();
            System.out.println("team loaded            : " + argentina);
            System.out.println("  ^ that SELECT read team only - no player query");

            // the raw field, not getPlayers() - the unmodifiable wrapper would
            // hide the Hibernate collection type we want to look at
            List<Player> squad = argentina.playersMutableForDemo();
            System.out.println("collection class       : " + squad.getClass().getName());
            System.out.println("  ^ NOT ArrayList. Hibernate swapped in its own List on load.");
            System.out.println("initialized?           : " + Hibernate.isInitialized(squad));
            System.out.println("--- calling size() now - watch a SELECT appear ---");
            System.out.println("size()                 : " + squad.size());
            System.out.println("initialized?           : " + Hibernate.isInitialized(squad));
            System.out.println("squad                  : " + squad);
        });
    }

    // ------------------------------------- 3. THE BIG ONE: inverse writes nothing
    private void inverseSideWritesNothing() {
        Integer loanId = tx.execute(s -> players.findByName("Loan Player").orElseThrow().getId());
        String before = currentTeamOf(loanId);

        System.out.println("\n===== 3. ADDING TO team.players PERSISTS NOTHING =====");
        System.out.println("'Loan Player' currently belongs to : " + before);

        tx.executeWithoutResult(status -> {
            Team argentina = teams.findByName("Argentina").orElseThrow();
            Player loan = players.findById(loanId).orElseThrow();

            System.out.println("--- argentina.playersMutableForDemo().add(loan)  (inverse side only) ---");
            argentina.playersMutableForDemo().add(loan);          // does NOT touch loan.team
            System.out.println("in-memory list now contains it: "
                    + argentina.playersMutableForDemo().contains(loan));
            System.out.println("--- transaction is about to commit; expect NO update ---");
        });

        String after = currentTeamOf(loanId);
        System.out.println("'Loan Player' in the database now : " + after);
        System.out.println(before.equals(after)
                ? "  UNCHANGED. The inverse side owns nothing - Hibernate ignored it entirely."
                : "  !! changed - unexpected");
    }

    // ---------------------------------------------------- 4. the owning side
    private void owningSideWrites() {
        Integer loanId = tx.execute(s -> players.findByName("Loan Player").orElseThrow().getId());
        String before = currentTeamOf(loanId);

        System.out.println("\n===== 4. SETTING player.team DOES PERSIST =====");
        System.out.println("'Loan Player' currently belongs to : " + before);

        tx.executeWithoutResult(status -> {
            Player loan = players.findById(loanId).orElseThrow();
            // flip between the two teams so this demo is re-runnable
            String target = "Brazil".equals(before) ? "Argentina" : "Brazil";
            Team destination = teams.findByName(target).orElseThrow();

            System.out.println("--- destination.addPlayer(loan) ---");
            System.out.println("    addPlayer() does BOTH: list.add(player) AND player.setTeam(this)");
            destination.addPlayer(loan);
            System.out.println("--- committing; expect an UPDATE on player.team_id ---");
        });

        System.out.println("'Loan Player' in the database now : " + currentTeamOf(loanId));
        System.out.println("  ^ the UPDATE came from player.setTeam(), never from the list.");
    }

    // ------------------------------------------- 5. collection outside a session
    private void outsideTransaction() {
        Team detached = tx.execute(s -> teams.findByName("Argentina").orElseThrow());

        System.out.println("\n===== 5. THE COLLECTION OUTSIDE A TRANSACTION =====");
        System.out.println("team (detached)        : " + detached);
        try {
            System.out.println("getPlayers().size()    : " + detached.getPlayers().size());
            System.out.println("  !! no exception - unexpected");
        } catch (LazyInitializationException e) {
            System.out.println("getPlayers().size()    : threw LazyInitializationException");
            System.out.println("  message: " + e.getMessage());
            System.out.println("  ^ same rule as a lazy @ManyToOne proxy. This is exactly what");
            System.out.println("    would happen if a controller returned a Team entity and");
            System.out.println("    Jackson tried to serialize its players. Hence DTOs.");
        }
    }

    // --------------------------------------- 6. join fetch on a collection
    private void joinFetchCollection() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 6. JOIN FETCH A COLLECTION =====");

            System.out.println("--- WITHOUT distinct ---");
            List<Team> plain = teams.findAllWithPlayers();
            System.out.println("rows returned : " + plain.size());
            for (Team t : plain) {
                System.out.println("   " + t.getName() + " -> " + t.getPlayers().size() + " players");
            }
            System.out.println("   ^ NO duplicates. The SQL result set IS flat - a team with 3");
            System.out.println("     players produces 3 rows - but Hibernate 6 de-duplicates the");
            System.out.println("     entity list for you. In Hibernate 5 this returned duplicates,");
            System.out.println("     which is why so much advice online says to add 'distinct'.");

            System.out.println("--- WITH distinct (no longer needed) ---");
            List<Team> distinct = teams.findAllWithPlayersDistinct();
            System.out.println("rows returned : " + distinct.size() + "   <- identical");
            System.out.println("   ^ LOOK AT THE SQL: the keyword was passed through as");
            System.out.println("     SELECT DISTINCT, so Postgres now sorts/hashes every row to");
            System.out.println("     remove duplicates that Hibernate would have dropped anyway.");
            System.out.println("     On Hibernate 6, distinct here is pure wasted database work.");
            System.out.println();
            System.out.println("   One query either way, every squad loaded, no N+1.");
        });
    }

    // ---------------------------------------------------------------- helpers
    private String currentTeamOf(Integer playerId) {
        return tx.execute(s -> players.findById(playerId).orElseThrow().getTeam().getName());
    }
}
