package com.example.worldcup.demo;

import com.example.worldcup.player.Player;
import com.example.worldcup.player.PlayerRepository;
import com.example.worldcup.role.Role;
import com.example.worldcup.role.RoleRepository;
import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 7 - how do you actually SAVE a Player together with its Team?
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * Answers two questions:
 *
 *   Q1. "New player AND new team - can I just build the object graph and
 *        save(player)?"
 *       -> NO. There is no cascade on @ManyToOne, so Hibernate refuses:
 *          TransientPropertyValueException. See scenario A.
 *
 *   Q2. "New player on an EXISTING team - how?"
 *       -> Load the team (or get a reference to it) and pass it in.
 *          See scenarios C and D.
 *
 * Nothing is deleted. Re-running is safe.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step7SavingAssociationsDemo implements CommandLineRunner {

    private final PlayerRepository players;
    private final TeamRepository teams;
    private final RoleRepository roles;
    private final TransactionTemplate tx;

    Step7SavingAssociationsDemo(PlayerRepository players, TeamRepository teams,
                                RoleRepository roles, PlatformTransactionManager txManager) {
        this.players = players;
        this.teams = teams;
        this.roles = roles;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        scenarioA_transientTeam();
        scenarioB_saveTeamFirst();
        scenarioC_existingTeamLoaded();
        scenarioD_existingTeamByReference();
        scenarioE_referenceToMissingRow();
        summary();
    }

    // ================================================================ SCENARIO A
    private void scenarioA_transientTeam() {
        System.out.println("\n===== A. NEW PLAYER + NEW TEAM, saved in one go (THE WRONG WAY) =====");
        System.out.println("new Player(\"Kylian Mbappe\", new Team(\"France\"), forward)");
        System.out.println("then players.save(player)  -- the Team was never saved");
        try {
            tx.executeWithoutResult(status -> {
                Role forward = role("Forward");
                Team france = new Team("France");          // TRANSIENT - no id, unknown to Hibernate
                Player mbappe = new Player("Kylian Mbappe", france, forward);
                players.save(mbappe);
            });
            System.out.println("!! it worked - unexpected");
        } catch (Exception e) {
            System.out.println("FAILED, as it should:");
            System.out.println("  exception : " + rootCause(e).getClass().getName());
            System.out.println("  message   : " + rootCause(e).getMessage());
            System.out.println("  ^ Hibernate will not silently insert the Team for you.");
            System.out.println("    @ManyToOne has NO cascade by default, and we deliberately");
            System.out.println("    left it that way - see the summary at the end.");
        }
        System.out.println("teams named 'France' in db: " + (teams.findByName("France").isPresent() ? 1 : 0)
                + "   <- the whole transaction rolled back");
    }

    // ================================================================ SCENARIO B
    private void scenarioB_saveTeamFirst() {
        System.out.println("\n===== B. NEW PLAYER + NEW TEAM (THE RIGHT WAY) =====");
        System.out.println("save the Team first, then hand the MANAGED Team to the Player");
        tx.executeWithoutResult(status -> {
            Role forward = role("Forward");

            Team france = teams.findByName("France")
                    .orElseGet(() -> {
                        System.out.println("--- teams.save(new Team(\"France\")) ---");
                        return teams.save(new Team("France"));
                    });
            System.out.println("team is now managed, id = " + france.getId());

            players.findByName("Kylian Mbappe").orElseGet(() -> {
                System.out.println("--- players.save(new Player(..., france, forward)) ---");
                return players.save(new Player("Kylian Mbappe", france, forward));
            });
        });
        System.out.println("done - two INSERTs, in the right order");
    }

    // ================================================================ SCENARIO C
    private void scenarioC_existingTeamLoaded() {
        System.out.println("\n===== C. NEW PLAYER ON AN EXISTING TEAM (load it) =====");
        System.out.println("teams.findByName(\"Argentina\") -> real SELECT -> pass to Player");
        tx.executeWithoutResult(status -> {
            Role keeper = role("Goalkeeper");

            System.out.println("--- loading the team ---");
            Team argentina = teams.findByName("Argentina").orElseThrow();
            System.out.println("loaded: " + argentina + "  (a real Team, fully populated)");

            players.findByName("Geronimo Rulli").orElseGet(() -> {
                System.out.println("--- saving the player ---");
                return players.save(new Player("Geronimo Rulli", argentina, keeper));
            });
        });
        System.out.println("this is the version you will use 95% of the time");
    }

    // ================================================================ SCENARIO D
    private void scenarioD_existingTeamByReference() {
        System.out.println("\n===== D. NEW PLAYER ON AN EXISTING TEAM (reference only) =====");
        System.out.println("getReferenceById(id) -> a PROXY, NO select, just to fill the FK");
        // look the id up in a SEPARATE transaction, so the team is NOT already
        // sitting in the persistence context when getReferenceById is called -
        // otherwise it would just hand back the cached instance, not a proxy
        Integer brazilId = tx.execute(status ->
                teams.findByName("Brazil").orElseThrow().getId());

        tx.executeWithoutResult(status -> {
            Role forward = role("Forward");

            System.out.println("--- getReferenceById(" + brazilId + ") : watch for NO select ---");
            Team brazilRef = teams.getReferenceById(brazilId);
            System.out.println("got: " + brazilRef.getClass().getSimpleName()
                    + "   <- a proxy; the team table was NOT read");

            players.findByName("Vinicius Junior").orElseGet(() -> {
                System.out.println("--- saving the player ---");
                return players.save(new Player("Vinicius Junior", brazilRef, forward));
            });
        });
        System.out.println("the INSERT only ever needed team_id, so reading the row was wasted work");
    }

    // ================================================================ SCENARIO E
    private void scenarioE_referenceToMissingRow() {
        System.out.println("\n===== E. THE TRAP IN getReferenceById =====");
        System.out.println("getReferenceById(999999) on a team that does not exist");
        try {
            tx.executeWithoutResult(status -> {
                Role forward = role("Forward");
                Team ghost = teams.getReferenceById(999999);
                System.out.println("getReferenceById returned without complaint: "
                        + ghost.getClass().getSimpleName());
                System.out.println("--- now saving a player that points at it ---");
                players.save(new Player("Ghost Player", ghost, forward));
            });
            System.out.println("!! it worked - unexpected");
        } catch (Exception e) {
            System.out.println("FAILED at flush time, not at call time:");
            System.out.println("  exception : " + rootCause(e).getClass().getSimpleName());
            System.out.println("  ^ findById gives you a clean, immediate 'not found'.");
            System.out.println("    getReferenceById defers the failure until the INSERT.");
            System.out.println("    That is why our services will use findById(...).orElseThrow().");
        }
    }

    // ================================================================== SUMMARY
    private void summary() {
        System.out.println("\n===== SUMMARY =====");
        System.out.println("Q1: new player + new team in one save()?  -> NO. Save the team first.");
        System.out.println("    (A cascade = CascadeType.PERSIST on @ManyToOne WOULD make it work,");
        System.out.println("     but we left it off on purpose: with cascade, a typo'd team name");
        System.out.println("     silently creates a duplicate team instead of failing.)");
        System.out.println();
        System.out.println("Q2: new player on an existing team?");
        System.out.println("    findById / findByName  -> 1 SELECT, gives a clean 404 if missing  <-- prefer this");
        System.out.println("    getReferenceById       -> 0 SELECT, but fails late if missing");
        System.out.println();
        tx.executeWithoutResult(status ->
                System.out.println("teams=" + teams.count()
                        + "  roles=" + roles.count()
                        + "  players=" + players.count()));
    }

    // ================================================================== helpers
    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(new Role(name)));
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
