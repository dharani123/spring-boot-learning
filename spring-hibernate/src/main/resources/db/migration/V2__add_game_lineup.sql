-- Which players appeared in which game.
--
-- A pure join table: two foreign keys and nothing else. The composite primary
-- key is what stops the same player being listed twice for one game.
--
-- If this relationship ever needs its own data - minutes played, goals, whether
-- the player started or came off the bench - this table must become an entity
-- of its own. A @ManyToMany cannot carry attributes.

create table game_player (
    game_id   int not null references game (id),
    player_id int not null references player (id),
    primary key (game_id, player_id)
);
