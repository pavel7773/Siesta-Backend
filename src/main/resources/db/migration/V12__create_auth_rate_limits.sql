
CREATE TABLE auth_rate_limits (
                                  scope VARCHAR(32) NOT NULL,
                                  key_hash CHAR(64) NOT NULL,
                                  window_start TIMESTAMPTZ NOT NULL,
                                  request_count INTEGER NOT NULL DEFAULT 0
                                      CHECK (request_count >= 0),

                                  PRIMARY KEY (
                                               scope,
                                               key_hash,
                                               window_start
                                      )
);

CREATE INDEX auth_rate_limits_window_idx
    ON auth_rate_limits (window_start);
