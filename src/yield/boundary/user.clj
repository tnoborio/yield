(ns yield.boundary.user
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defprotocol UserDatabase
  (find-user-by-email [db email])
  (create-user! [db user-data])
  (create-reset-token! [db user-id token expires-at])
  (find-reset-token [db token])
  (mark-token-used! [db token])
  (update-user-password! [db user-id password-hash]))

(extend-protocol UserDatabase
  javax.sql.DataSource
  (find-user-by-email [db email]
    (jdbc/execute-one! db
      ["SELECT id, email, password_hash, created_at FROM users WHERE email = ?" email]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-user! [db {:keys [email password-hash]}]
    (jdbc/execute-one! db
      ["INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id, email, created_at"
       email password-hash]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (create-reset-token! [db user-id token expires-at]
    (jdbc/execute-one! db
      ["INSERT INTO password_reset_tokens (user_id, token, expires_at) VALUES (?, ?, ?)"
       user-id token expires-at]))

  (find-reset-token [db token]
    (jdbc/execute-one! db
      ["SELECT rt.id, rt.user_id, rt.token, rt.expires_at, rt.used, u.email
        FROM password_reset_tokens rt
        JOIN users u ON u.id = rt.user_id
        WHERE rt.token = ? AND rt.used = false AND rt.expires_at > now()"
       token]
      {:builder-fn rs/as-unqualified-kebab-maps}))

  (mark-token-used! [db token]
    (jdbc/execute-one! db
      ["UPDATE password_reset_tokens SET used = true WHERE token = ?" token]))

  (update-user-password! [db user-id password-hash]
    (jdbc/execute-one! db
      ["UPDATE users SET password_hash = ?, updated_at = now() WHERE id = ?"
       password-hash user-id])))
