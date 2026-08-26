create database worldCup;

create table team (
id int primary key,
name varchar(19)
);

create table role (
id int primary key,
name varchar(29)
);

create table player (
id int primary key,
name varchar (139),
teamId int,
roleId int,
foreign key (teamId) references team(id),
foreign key (roleId) references role(id)
);

create table game (
id int primary key,
team1_id int,
team2_id int,
foreign key (team1_id) references team(id),
foreign key (team2_id) references team(id)
);

create table result (
id int primary key,
gameId int,
winningTeamId int,
losingTeamId int,
playerOfTheMatchId int,
foreign key (gameId) references game(id),
foreign key (winningTeamId) references team(id),
foreign key (losingTeamId) references team(id),
foreign key (playerOfTheMatchId) references player(id)
);