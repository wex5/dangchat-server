CREATE TABLE "public"."auth_token_transactions" (
"transaction_hash" varchar(255) COLLATE "default" NOT NULL,
"app_id" int4 NOT NULL,
"api_key" varchar(255) COLLATE "default" NOT NULL,
"device_hash" bytea NOT NULL,
"device_title" varchar(255) COLLATE "default" NOT NULL,
"access_salt" varchar(255) COLLATE "default" NOT NULL,
"is_checked" bool NOT NULL,
"deleted_at" timestamp(6),
"token" varchar(32) COLLATE "default" NOT NULL,
"device_info" bytea DEFAULT '\x'::bytea NOT NULL
)