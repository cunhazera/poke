-- Five users exist by default. 'ada' is the board admin (tasks.board.admin-username), notified about overdue tasks.
INSERT INTO app_user (full_name, username, email) VALUES
    ('Ada Lovelace',     'ada',    'ada@example.com'),
    ('Alan Turing',      'alan',   'alan@example.com'),
    ('Grace Hopper',     'grace',  'grace@example.com'),
    ('Linus Torvalds',   'linus',  'linus@example.com'),
    ('Margaret Hamilton','margaret','margaret@example.com');
