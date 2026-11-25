# MovioXP 3.1.0-qa2 Central mobile sign-in

This package adds the HTTPS phone sign-in page and pairing endpoint. It uses Central’s existing
`POST /movio/api/index.php/login` and `POST /movio/api/index.php/logout` JSON endpoints.
The TV continues to retrieve profiles through the existing authenticated Central API.
Direct TV sign-in remains available before this deployment.

## Install on Central

1. Back up the Central database. Apply `migrations/mobile_login.sql` once to the Central MySQL or
   MariaDB database. It adds two InnoDB tables and leaves existing Central tables untouched.
2. In the Movio Central vhost, use the directory layout below. Here `<vhost>` is the directory
   that contains both the web document root and the existing Central `private` directory.
   If the document root is named `httpdocs`, substitute it for `public`.

   | Package path | Destination |
   | --- | --- |
   | `public/movio/api/mobile-login/index.php` | `<vhost>/public/movio/api/mobile-login/index.php` |
   | `public/movio/api/mobile-login/pair.php` | `<vhost>/public/movio/api/mobile-login/pair.php` |
   | `public/movio/api/mobile-login/connect.css` | `<vhost>/public/movio/api/mobile-login/connect.css` |
   | `public/movio/api/mobile-login/connect.js` | `<vhost>/public/movio/api/mobile-login/connect.js` |
   | `private/movioxp_mobile_config.example.php` | `<vhost>/private/movioxp_mobile_config.php` |

3. Enable PHP 8.1+ with PDO MySQL, Sodium and cURL. Use a dedicated database credential with
   SELECT, INSERT and UPDATE permissions on the two pairing tables. Allow DELETE for the cleanup job.
   Configure the private PHP file with those credentials and a fresh 32-byte key in hex
   (`openssl rand -hex 32`). Keep the file outside the document root and readable only by the
   deployment owner and PHP service. Leave the Central API URL set to the same HTTPS deployment
   that the app uses, with a trailing slash after `index.php`.
4. Serve the directory at `https://moviotv.opticalzoo.com/movio/api/mobile-login/` with
   `index.php` as its directory index. Exempt this directory from any catch-all API rewrite.
   The PHP HTTPS flag must be set by the web server on TLS requests and `REMOTE_ADDR` must
   be trustworthy. Configure trusted proxy handling at the web server if needed.
   PHP must be able to reach the existing Central HTTPS API, including when hosted on the same
   vhost. Preserve TLS certificate validation. Disable request and response body capture for
   the pairing and existing login/logout endpoints in the web server, WAF, tracing and PHP diagnostics.
   Keep Central's existing login rate limits enabled. The pairing route adds per-IP limits and caps
   each QR at five authentication attempts.
5. Run these statements every five minutes using the database scheduler or your existing cron job.

   ```sql
   DELETE FROM movioxp_mobile_pairs WHERE expires_at <= UNIX_TIMESTAMP();
   DELETE FROM movioxp_mobile_limits WHERE expires_at <= UNIX_TIMESTAMP();
   ```

Pairing expires after 180 seconds. The QR contains one phone credential in its URL fragment.
The TV claim uses a different secret. The one-time claim removes the encrypted bearer from
the pairing row before returning it to the TV. Only Central receives the submitted credentials.
The TV stores its bearer through the existing Android Keystore-backed connection store.

## Staging checks

- Open the mobile route without a QR and verify it asks you to scan the TV.
- Scan a QR from each TV login entry point. Test incorrect credentials, a successful sign-in,
  cancel on the TV, expiration and a second claim of a used pairing.
- Sign in with two test Central accounts. Confirm each profile picker shows only that account’s
  profiles and that signing back into the first account recovers its local data and unsynced changes.
- Check that direct TV login still works during a temporary outage of the mobile route.

To roll back, remove the public mobile directory and its private config file, then stop the
cleanup job. The two added tables can be dropped after the mobile route has been disabled.
