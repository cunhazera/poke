# Collection generators

The Postman collections in `../collections/` are **the source of truth**. Newman runs them, and Postman can import them.
These scripts are a convenience for writing them: a request is a few lines of Python here, compared with ~40 lines of Postman JSON.

| Script | Generates | Notes |
|---|---|---|
| `postman.py` | — | Shared builders: `req(...)`, `unauthorized(...)`, `LOGIN_AS_ASH` (demo user setup) |
| `auth_users.py` | `auth-users.postman_collection.json` | Reproduces the committed file exactly |
| `us03_data_sync.py` | `us03-data-sync.postman_collection.json` | Reproduces the committed file exactly |

**US01 and US02 have no generator.** Their JSON was patched after generation (the auth checks in their "HTTP semantics" folders), so it's edited directly.

```bash
python3 -I api-tests/generators/us03_data_sync.py   # rewrites the collection; review the git diff, then run the collections
```

For a new story, copy one of the story generators, reuse `postman.py`, and commit both the script and the generated JSON.
