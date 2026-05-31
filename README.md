# Boa SQL
A library for a better SQL

## Installation

Add to your dependency list:

```clojure
[org.clojars.jj/boa-sql "1.0.12"]
```

## Usage

Create a query file in your resources directory with variables marked with `:variable`

### Next.JDBC (synchronous)

```clojure
(require [jj.sql.boa :as boa]
         [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]])

(def query (boa/build-query (->NextJdbcAdapter) "query-in-resource.sql"))

(query data-source {:user-id 42})
```

### Next.JDBC (async)

Returns `CompletableFuture` for each query.

```clojure
(require [jj.sql.boa :as boa]
         [jj.sql.boa.query.next-jdbc-async :refer [->NextJdbcAsyncAdapter]])

(def query (boa/build-query (->NextJdbcAsyncAdapter) "query-in-resource.sql"))

(.get (query data-source {:user-id 42}))
```

## Query Builders

Each adapter carries a query builder that controls how SQL placeholders are generated.

### JDBCStrategy (default)

Uses `?` placeholders and map arguments. This is the default for all built-in adapters.

```clojure
;; Query file: SELECT * FROM users WHERE name = :name AND age = :age
;; Produces:   SELECT * FROM users WHERE name = ? AND age = ?
;; Arguments:  {:name "John" :age 20}
```

### SequentialStrategy

Uses `$1`, `$2` placeholders and vector arguments. Useful for drivers that use positional parameters.

```clojure
(require [jj.sql.boa.query.next-jdbc :refer [->NextJdbcAdapter]]
         [jj.sql.boa.strategy.sequential :refer [->SequentialStrategy]]
         [next.jdbc.result-set :as rs])
(import [jj.sql.boa.query.next_jdbc NextJdbcAdapter])

;; Pass a custom strategy when creating an adapter
(def adapter (NextJdbcAdapter. {:builder-fn rs/as-unqualified-lower-maps} (->SequentialStrategy)))

;; Query file: SELECT * FROM users WHERE name = :name AND age = :age
;; Produces:   SELECT * FROM users WHERE name = $1 AND age = $2
;; Arguments:  ["John" 20]
```

## Query Examples

### Single value

```clojure
;; Query: SELECT * FROM users WHERE user_id = :user-id;
(query data-source {:user-id 123})
;; Produces: SELECT * FROM users WHERE user_id = ?;
;; Parameters: [123]
```

### Tuple

```clojure
;; Query: INSERT INTO numbers (first, second, third) VALUES :numbers
(query data-source {:numbers [1 2 3]})
;; Produces: INSERT INTO numbers (first, second, third) VALUES (?,?,?)
;; Parameters: [1 2 3]
```

### Sequence of tuples

```clojure
;; Query: INSERT INTO users (name, age) VALUES :users
(query data-source {:users [["Alice" 30] ["Bob" 25]]})
;; Produces: INSERT INTO users (name, age) VALUES (?,?),(?,?)
;; Parameters: ["Alice" 30 "Bob" 25]
```

### Conditional blocks

Use `--- IF :variable` / `--- ENDIF` to include a SQL fragment only when a parameter is present (non-nil).

```sql
-- resources/users/search.sql
SELECT id, name, email
FROM users
--- IF :offset
ORDER BY id
LIMIT 20 OFFSET :offset
--- ENDIF
```

```clojure
(query data-source {})            ;; SELECT id, name, email FROM users
(query data-source {:offset 40})  ;; SELECT id, name, email FROM users ORDER BY id LIMIT 20 OFFSET ?
```

Add `--- ELSE` to provide a fallback fragment when the parameter is absent:

```sql
-- resources/users/search.sql
SELECT id, name, email
FROM users
--- IF :limit
ORDER BY id LIMIT :limit
--- ELSE
ORDER BY id
--- ENDIF
```

```clojure
(query data-source {})           ;; ... ORDER BY id
(query data-source {:limit 10})  ;; ... ORDER BY id LIMIT ?
```

The condition variable (`:offset`, `:limit`) doubles as a SQL parameter placeholder inside the block when referenced with `:variable` syntax. Using it only in the `--- IF` line (with a hardcoded value in the body) is also valid.

## Tested on

| database   |
|------------|
| H2         |
| SQLite     |
| MariaDB    |
| PostgreSQL |

## License

Copyright © 2025 [ruroru](https://github.com/ruroru)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
