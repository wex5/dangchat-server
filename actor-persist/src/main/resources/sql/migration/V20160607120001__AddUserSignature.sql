CREATE TABLE IF NOT EXISTS user_signatures (
  user_id int4 NOT NULL,
  signature bytea NOT NULL,
  expire int4 NOT NULL,
  base_url varchar(255) NOT NULL,
  PRIMARY KEY(user_id)
);