package com.example.spring_jdbc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class SpringJdbcApplication implements CommandLineRunner {

	@Autowired
	public JdbcTemplate jdbcTemplate;

	public static void main(String[] args) {
		SpringApplication.run(SpringJdbcApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		//jdbcTemplate.execute("create table employee(id int, name varchar(255), salary int)");

		jdbcTemplate.execute("Insert into employee values(2,'Dharani', 20000)");
	}
}
