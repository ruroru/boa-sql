    # Boa SQL
A library for a better SQL

## Installation

Add to your dependency list:

```clojure
[org.clojars.jj/boa-sql "1.0.2"]
```

## Usage

Create a query file in your resources directory with variables marked with ``:variable``

```clojure
(require [jj.sql.boa :as boa])

(def query (boa/build-query (boa/->NextJdbcAdapter) "query-in-resource.sql"))

;; Execute with context
(query data-source {:user-id 42})
```

or async
```clojure
(require [jj.sql.boa :as boa])

(def async-query (boa/build-async-query executor (boa/->NextJdbcAdapter) "query-in-resource.sql"))

;; Execute with context
(async-query data-source {:user-id 42} respnd raise)
```

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

## Tested on

| databse    |
|------------|
| mariadb    |
| mysql      |
| postgresql |
| h2         |
| derby      |
| sqlite     |

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
