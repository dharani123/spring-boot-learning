package com.example.worldcup.demo;

import com.example.worldcup.team.Team;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4 - the @Entity mapping, exercised through a raw EntityManager.
 *
 * To run this demo:
 *   uncomment @Component below, then just Run the application.
 *
 * What to look for:
 *   - id is null before persist, populated after  -> @GeneratedValue(IDENTITY)
 *   - the generated INSERT has no id column       -> the database assigns it
 *   - find() returns the SAME object reference    -> the persistence context
 */
// @Component   <-- uncomment to run this demo (and comment out the others)
class Step4EntityDemo implements CommandLineRunner {

    private final EntityManager em;

    Step4EntityDemo(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("\n===== STEP 4: ENTITY =====");

        Team t = new Team("Argentina");
        System.out.println("BEFORE persist : " + t + "   <- id is null, object is 'transient'");

        em.persist(t);
        System.out.println("AFTER persist  : " + t + "   <- database generated the id");

        Team found = em.find(Team.class, t.getId());
        System.out.println("find(id)       : " + found);
        System.out.println("found == t ?   : " + (found == t)
                + "   <- persistence context returned the SAME instance, no SELECT issued");
    }
}
