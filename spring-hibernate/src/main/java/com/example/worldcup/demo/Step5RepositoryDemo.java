package com.example.worldcup.demo;

import com.example.worldcup.team.Team;
import com.example.worldcup.team.TeamRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 5 - Spring Data JPA repositories.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   - the injected object is a PROXY, not a class you wrote
 *   - you declared 3 methods but can call ~26
 *   - method NAMES became SQL - no query is written anywhere
 *   - the final UPDATE is issued without any save() call
 *
 * Nothing is ever deleted. Re-running is safe: rows are created only if missing.
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step5RepositoryDemo implements CommandLineRunner {

    private final TeamRepository repo;

    Step5RepositoryDemo(TeamRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {

        System.out.println("\n===== WHAT DID SPRING ACTUALLY INJECT? =====");
        System.out.println("declared type      : " + TeamRepository.class.getName());
        System.out.println("is it an interface?: " + TeamRepository.class.isInterface());
        System.out.println("runtime class      : " + repo.getClass().getName());
        System.out.println("methods I declared : "
                + TeamRepository.class.getDeclaredMethods().length);
        System.out.println("methods callable   : "
                + Arrays.stream(TeamRepository.class.getMethods())
                        .map(Method::getName).distinct().count());

        System.out.println("\n===== SEED DATA (INSERT only if missing) =====");
        findOrCreate("Argentina");
        findOrCreate("Bosnia and Herzegovina");
        // this row's name flips between spellings on every run - see DIRTY CHECKING below
        Team brazil = repo.findByName("Brazil")
                .or(() -> repo.findByName("Brasil"))
                .orElseGet(() -> repo.save(new Team("Brazil")));
        System.out.println("brazil row -> " + brazil);

        System.out.println("\n===== COUNT + FIND ALL =====");
        System.out.println("count()   : " + repo.count());
        System.out.println("findAll() : " + repo.findAll());

        System.out.println("\n===== findById -> Optional =====");
        System.out.println("findById(" + brazil.getId() + ") : " + repo.findById(brazil.getId()));
        System.out.println("findById(9999)  : " + repo.findById(9999)
                + "   <- empty Optional, NOT null");

        System.out.println("\n===== DERIVED QUERY: findByName =====");
        System.out.println("findByName(\"Argentina\") : " + repo.findByName("Argentina"));
        System.out.println("findByName(\"Narnia\")    : " + repo.findByName("Narnia"));

        System.out.println("\n===== DERIVED QUERY: existsByName =====");
        System.out.println("existsByName(\"Argentina\") : " + repo.existsByName("Argentina")
                + "   <- SQL selects only the id, 'fetch first 1 rows' - no full row loaded");

        System.out.println("\n===== DERIVED QUERY: Containing + IgnoreCase + OrderBy =====");
        System.out.println("fragment 'a' -> "
                + repo.findByNameContainingIgnoreCaseOrderByNameAsc("a"));

        System.out.println("\n===== DIRTY CHECKING =====");
        Team managed = repo.findById(brazil.getId()).orElseThrow();
        String flipped = managed.getName().equals("Brazil") ? "Brasil" : "Brazil";
        managed.setName(flipped);
        System.out.println("renamed to '" + flipped + "' on a MANAGED entity.");
        System.out.println("save() was NEVER called.");
        System.out.println("The UPDATE appears BELOW this line - Hibernate flushes at commit,");
        System.out.println("which happens when this @Transactional method returns.");

        System.out.println("\n===== DATA STAYS =====");
        System.out.println("Nothing is deleted. Stop the app, run it again:");
        System.out.println("count() will still be " + repo.count()
                + " and Brazil/Brasil will have flipped once more.");
    }

    private void findOrCreate(String name) {
        repo.findByName(name).orElseGet(() -> repo.save(new Team(name)));
    }
}
