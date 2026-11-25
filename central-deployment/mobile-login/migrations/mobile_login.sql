CREATE TABLE IF NOT EXISTS movioxp_mobile_pairs (
  tv_hash BINARY(32) PRIMARY KEY,
  phone_hash BINARY(32) NOT NULL UNIQUE,
  expires_at BIGINT UNSIGNED NOT NULL,
  state ENUM('waiting','authenticating','ready','claimed','cancelled') NOT NULL DEFAULT 'waiting',
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  sealed_session BLOB NULL,
  INDEX (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS movioxp_mobile_limits (
  bucket BINARY(32) PRIMARY KEY,
  expires_at BIGINT UNSIGNED NOT NULL,
  hits INT UNSIGNED NOT NULL,
  INDEX (expires_at)
) ENGINE=InnoDB;
