

CREATE TABLE Users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    user_role TEXT CHECK ( user_role IN ('USER', 'ADMIN') ) DEFAULT 'USER' NOT NULL ,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL

)