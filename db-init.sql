CREATE DATABASE electionbot;

USE electionbot;

CREATE TABLE contest(
contest_id int(10) not null,
title varchar(255) not null,
PRIMARY KEY (`contest_id`)
);


CREATE TABLE candidate(
candidate_id int(10) not null,
name varchar(255) not null,
votes int(10) not null,
contest_id int(10) not null,
updated_on timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`candidate_id`),
CONSTRAINT `fk_candidate_contest_id` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`contest_id`)
);
