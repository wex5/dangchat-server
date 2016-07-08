CREATE TABLE "public"."client_state" (
"user_id" int4 NOT NULL,
"state" int2
)
WITH (OIDS=FALSE);

ALTER TABLE "public"."client_state" ADD PRIMARY KEY ("user_id");