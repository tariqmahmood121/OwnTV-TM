# MovioXP 3.1.0-qa5

Signed Android QA build, version code 100096.

- Restores TV-hosted QR phone entry for initial setup, Central sign-in, account switching, and manual add-on URLs.
- The phone page uses a temporary PIN, expires after three minutes, and closes after submission or cancellation. MovioXP uses its existing HTTPS Central login API.
- Use a trusted local network for phone entry. The phone-to-TV leg uses local HTTP.

Local HTTP tests covered QR decoding, incorrect PIN rejection, submission for both forms, replay prevention, expiry, and cancellation. Physical phone-to-TV and real-account sign-in checks remain pending.
