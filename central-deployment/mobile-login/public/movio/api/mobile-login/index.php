<?php
declare(strict_types=1);

if (empty($_SERVER['HTTPS']) || $_SERVER['HTTPS'] === 'off') {
    http_response_code(403);
    exit;
}
header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store');
header('Referrer-Policy: no-referrer');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header("Content-Security-Policy: default-src 'none'; style-src 'self'; script-src 'self'; connect-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'");
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Connect your TV | MovioXP</title>
    <link rel="stylesheet" href="connect.css">
    <script src="connect.js" defer></script>
</head>
<body>
<main>
    <div class="brand"><span class="play" aria-hidden="true">▶</span>MovioXP</div>
    <h1>Connect your TV</h1>
    <p id="status" role="status" aria-live="polite">Checking your TV connection…</p>
    <form id="login" hidden>
        <label for="username">Username</label>
        <input id="username" autocomplete="username" autocapitalize="none" spellcheck="false" maxlength="512" required>
        <label for="password">Password</label>
        <div class="password-field">
            <input id="password" type="password" autocomplete="current-password" maxlength="4096" required>
            <button id="visibility" type="button" aria-controls="password" aria-pressed="false">Show</button>
        </div>
        <button class="submit" type="submit">Sign in &amp; Connect</button>
    </form>
    <p class="secure">Secure connection to your TV</p>
    <p class="hint">You can close this page after connecting.</p>
    <noscript><p>Enable JavaScript to connect to your TV.</p></noscript>
</main>
</body>
</html>
