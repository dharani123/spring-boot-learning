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
 * Step 6 - @ManyToOne: Player -> Team and Player -> Role.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   1. the INSERT writes team_id / role_id - a FK column, not a nested object
 *   2. getTeam() returns a PROXY class, not a Team, until you touch it
 *   3. touching the proxy fires a separate SELECT (lazy loading)
 *   4. the same proxy OUTSIDE a transaction throws LazyInitializationException
 *   5. N+1: one query for players, then one more per player for its team
 *   6. join fetch collapses all of it into a single query
 *
 * Why TransactionTemplate instead of @Transactional?
 *   @Transactional works by wrapping the bean in a proxy. A call from run() to
 *   this.someMethod() does NOT go through that proxy, so the annotation is
 *   silently ignored - a classic Spring trap. TransactionTemplate has no such
 *   subtlety: the transaction starts and ends exactly where you see the lambda.
 *
 * Nothing is deleted. Re-running is safe.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step6ManyToOneDemo implements CommandLineRunner {

    private final PlayerRepository players;
    private final TeamRepository teams;
    private final RoleRepository roles;
    private final TransactionTemplate tx;

    Step6ManyToOneDemo(PlayerRepository players, TeamRepository teams,
                       RoleRepository roles, PlatformTransactionManager txManager) {
        this.players = players;
        this.teams = teams;
        this.roles = roles;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        seed();
        lazyInsideTransaction();
        lazyOutsideTransaction();
        nPlusOne();
        joinFetch();
        derivedQueries();
    }

    // ------------------------------------------------------------------ 1. seed
    private void seed() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 1. SEED (inserts only what is missing) =====");
            Team argentina = team("Argentina");
            Team brazil    = team("Brazil");
            Role forward   = role("Forward");
            Role keeper    = role("Goalkeeper");

            player("Lionel Messi", argentina, forward);
            player("Emiliano Martinez", argentina, keeper);
            player("Neymar Jr", brazil, forward);
            player("Alisson Becker", brazil, keeper);

            System.out.println("players in db: " + players.count());
        });
    }

    // ------------------------------------------- 2. lazy loading, session open
    private void lazyInsideTransaction() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 2. LAZY LOADING INSIDE A TRANSACTION =====");
            Player messi = players.findByName("Lionel Messi").orElseThrow();
            System.out.println("player loaded           : " + messi);
            System.out.println("  ^ the SELECT above read ONLY the player table");

            Team t = messi.getTeam();
            System.out.println("getTeam() runtime class : " + t.getClass().getName());
            System.out.println("  ^ a Hibernate-generated subclass of Team, not Team itself");
            System.out.println("initialized yet?        : " + Hibernate.isInitialized(t));
            System.out.println("getId() on the proxy    : " + t.getId()
                    + "   <- FREE: the id was already in player.team_id, no query");

            System.out.println("--- calling getName() now - watch a SELECT appear ---");
            String name = t.getName();
            System.out.println("getName() -> " + name);
            System.out.println("initialized now?        : " + Hibernate.isInitialized(t));
        });
    }

    // ------------------------------------------ 3. same proxy, session closed
    private void lazyOutsideTransaction() {
        Player detached = tx.execute(status ->
                players.findByName("Neymar Jr").orElseThrow());

        System.out.println("\n===== 3. THE SAME THING OUTSIDE A TRANSACTION =====");
        System.out.println("the transaction above has COMMITTED - this player is now 'detached'");
        System.out.println("player                  : " + detached);
        System.out.println("getTeam().getId()       : " + detached.getTeam().getId()
                + "   <- still fine, the FK value was already loaded");
        try {
            System.out.println("getTeam().getName()     : " + detached.getTeam().getName());
            System.out.println("  !! no exception - unexpected here");
        } catch (LazyInitializationException e) {
            System.out.println("getTeam().getName()     : threw LazyInitializationException");
            System.out.println("  message: " + e.getMessage());
            System.out.println("  ^ THE most common Hibernate error. The session is closed,");
            System.out.println("    so the proxy has no connection on which to run its SELECT.");
            System.out.println("    Fixes: load it inside the transaction, or use join fetch (see 5).");
        }
    }

    // ------------------------------------------------------------- 4. the N+1
    private void nPlusOne() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 4. THE N+1 PROBLEM =====");
            System.out.println("--- findAll() : expect ONE select ---");
            List<Player> all = players.findAll();
            System.out.println("got " + all.size() + " players (no team data yet)");

            System.out.println("--- now touching each team: expect " + all.size()
                    + " MORE selects ---");
            for (Player p : all) {
                System.out.println("   " + p.getName() + " plays for " + p.getTeam().getName());
            }
            System.out.println("total: 1 + " + all.size()
                    + " queries. With 1000 players that is 1001 round-trips.");
        });
    }

    // -------------------------------------------------------------- 5. the cure
    private void joinFetch() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 5. JOIN FETCH - THE CURE =====");
            System.out.println("--- findAllWithTeamAndRole() : ONE select containing JOINs ---");
            List<Player> all = players.findAllWithTeamAndRole();
            for (Player p : all) {
                System.out.println("   " + p.getName()
                        + " | " + p.getTeam().getName()
                        + " | " + p.getRole().getName());
            }
            System.out.println("total: 1 query. Nothing was lazy-loaded - it was already in memory.");
        });
    }

    // ------------------------------------------------ 6. querying the association
    private void derivedQueries() {
        tx.executeWithoutResult(status -> {
            System.out.println("\n===== 6. DERIVED QUERIES ACROSS THE ASSOCIATION =====");
            System.out.println("--- findByTeamName(\"Argentina\") : walks player -> team -> name ---");
            System.out.println(players.findByTeamName("Argentina"));

            Integer argentinaId = teams.findByName("Argentina").orElseThrow().getId();
            System.out.println("--- findByTeamId(" + argentinaId + ") ---");
            System.out.println("    LOOK AT THE SQL: it JOINS team even though player.team_id");
            System.out.println("    already holds the value. Spring Data read 'TeamId' as a");
            System.out.println("    walk across the association, not as the FK column.");
            System.out.println(players.findByTeamId(argentinaId));
        });
    }

    // ----------------------------------------------------------------- helpers
    private Team team(String name) {
        return teams.findByName(name).orElseGet(() -> teams.save(new Team(name)));
    }

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(new Role(name)));
    }

    private void player(String name, Team team, Role role) {
        players.findByName(name).orElseGet(() -> players.save(new Player(name, team, role)));
    }
}
