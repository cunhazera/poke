-- Demo accounts, loaded only by the `demo` profile (application-demo.properties adds this location).
-- Repeatable migration: re-applied when this file changes; ON CONFLICT keeps it safe to re-run.
-- Hashes: BCrypt strength 10, made with Spring Security's BCryptPasswordEncoder. Passwords are in README.md.
INSERT INTO app_user (username, email, password_hash, role) VALUES
    ('ash', 'ash@pallet.town', '$2a$10$2HvW8YNDbqlp849b1bImGu4nacTrVK6HKAw.tUELtYhiqIXZ5eBp6', 'USER'),   -- pikachu123
    ('oak', 'oak@pallet.town', '$2a$10$UzrwqbxYtSElgDFKsvqCQ.Otjs/m8bnUJvZphpBFmZP7RvJx7VTgm', 'ADMIN')   -- professor123
ON CONFLICT DO NOTHING;
