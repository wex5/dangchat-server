CREATE TABLE "public"."user_bots" (
  "id" int4 NOT NULL,
  "nickname" varchar(255) COLLATE "default" NOT NULL,
  "name" varchar(255) COLLATE "default" NOT NULL,
  "token" varchar(64) COLLATE "default" NOT NULL,
  CONSTRAINT "bots_pkey" PRIMARY KEY ("id")
);