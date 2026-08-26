package com.example.helloworld.controller;

import com.example.helloworld.model.Greeting;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {


    @GetMapping("greeting")
    public Greeting hello(@RequestParam(defaultValue = "World") String name) {
        return new Greeting(1, name);
    }
}
