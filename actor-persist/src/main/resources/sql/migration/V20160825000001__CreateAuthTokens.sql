CREATE TABLE "public"."auth_tokens" (
"user_id" int4 NOT NULL,
"token" varchar(255) COLLATE "default" NOT NULL,
"created_at" timestamp(6) NOT NULL,
"attempts" int4 DEFAULT 0 NOT NULL,
CONSTRAINT "auth_tokens_pkey" PRIMARY KEY ("user_id")
)
WITH (OIDS=FALSE);