<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

function reply(array $body, int $status = 200): never
{
    http_response_code($status);
    echo json_encode($body, JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR);
    exit;
}

function secretHash(mixed $secret): string
{
    if (!is_string($secret) || !preg_match('/\A[0-9a-f]{64}\z/', $secret)) {
        reply(['state' => 'invalid'], 400);
    }
    return hash('sha256', $secret, true);
}

function sql(PDO $db, string $query, array $values): PDOStatement
{
    $statement = $db->prepare($query);
    $statement->execute($values);
    return $statement;
}

function limit(PDO $db, string $namespace, string $subject, int $max, int $window, string $key): void
{
    $now = time();
    $bucket = hash_hmac('sha256', "$namespace:" . intdiv($now, $window) . ":$subject", $key, true);
    // Increment and read under the same row lock so concurrent hits are counted independently.
    $db->beginTransaction();
    sql($db, 'INSERT INTO movioxp_mobile_limits VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE hits = hits + 1',
        [$bucket, $now + $window]);
    $hits = (int) sql($db, 'SELECT hits FROM movioxp_mobile_limits WHERE bucket = ?', [$bucket])->fetchColumn();
    $db->commit();
    if ($hits > $max) {
        header('Retry-After: ' . ($window - $now % $window));
        reply(['state' => 'limited'], 429);
    }
}

function centralRequest(string $url, array $body, ?string $token = null): array
{
    $curl = curl_init($url);
    $headers = ['Content-Type: application/json', 'Accept: application/json'];
    if ($token !== null) {
        $headers[] = 'Authorization: Bearer ' . $token;
    }
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => json_encode((object) $body, JSON_THROW_ON_ERROR),
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_CONNECTTIMEOUT => 4,
        CURLOPT_TIMEOUT => 10,
    ]);
    $response = curl_exec($curl);
    $status = curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    curl_close($curl);
    return [$status, is_string($response) ? json_decode($response, true) : null];
}

try {
    if (empty($_SERVER['HTTPS']) || $_SERVER['HTTPS'] === 'off') {
        reply(['state' => 'unavailable'], 403);
    }
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        header('Allow: POST');
        reply(['state' => 'invalid'], 405);
    }
    if (strtolower(trim(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0])) !== 'application/json') {
        reply(['state' => 'invalid'], 415);
    }
    $raw = file_get_contents('php://input', false, null, 0, 8193);
    if ($raw === false || strlen($raw) > 8192) {
        reply(['state' => 'invalid'], 413);
    }
    $body = json_decode($raw, true, 8);
    $action = $body['action'] ?? null;
    if (!in_array($action, ['start', 'claim', 'cancel', 'connect', 'status'], true)) {
        reply(['state' => 'invalid'], 400);
    }

    $config = require dirname(__DIR__, 4) . '/private/movioxp_mobile_config.php';
    $key = sodium_hex2bin($config['key_hex']);
    if (strlen($key) !== SODIUM_CRYPTO_SECRETBOX_KEYBYTES ||
        !str_starts_with($config['central_api'], 'https://') ||
        !str_ends_with($config['central_api'], '/')) {
        throw new RuntimeException();
    }
    $db = new PDO($config['dsn'], $config['db_user'], $config['db_password'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    $ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
    $now = time();

    if ($action === 'start') {
        limit($db, 'start', $ip, 12, 60, $key);
        $tv = bin2hex(random_bytes(32));
        $phone = bin2hex(random_bytes(32));
        sql($db, 'INSERT INTO movioxp_mobile_pairs (tv_hash, phone_hash, expires_at) VALUES (?, ?, ?)',
            [secretHash($tv), secretHash($phone), $now + 180]);
        reply(['tv_secret' => $tv, 'phone_secret' => $phone]);
    }

    if ($action === 'claim' || $action === 'cancel') {
        limit($db, 'tv', $ip, 180, 60, $key);
        $tv = secretHash($body['tv_secret'] ?? null);
        $db->beginTransaction();
        $pair = sql($db, 'SELECT state, sealed_session FROM movioxp_mobile_pairs
            WHERE tv_hash = ? AND expires_at > ? FOR UPDATE', [$tv, $now])->fetch(PDO::FETCH_ASSOC);
        if (!$pair || in_array($pair['state'], ['cancelled', 'claimed'], true)) {
            $db->commit();
            reply(['state' => 'expired']);
        }

        $session = null;
        if ($pair['state'] === 'ready') {
            $sealed = $pair['sealed_session'];
            $length = SODIUM_CRYPTO_SECRETBOX_NONCEBYTES;
            $plaintext = sodium_crypto_secretbox_open(substr($sealed, $length), substr($sealed, 0, $length), $key);
            if ($plaintext === false) {
                throw new RuntimeException();
            }
            $session = json_decode($plaintext, true, 8, JSON_THROW_ON_ERROR);
        }
        if ($action === 'cancel') {
            sql($db, "UPDATE movioxp_mobile_pairs SET state = 'cancelled', sealed_session = NULL WHERE tv_hash = ?", [$tv]);
            $db->commit();
            if ($session !== null) {
                centralRequest($config['central_api'] . 'logout', [], $session['token']);
            }
            reply(['state' => 'cancelled']);
        }
        if ($session === null) {
            $db->commit();
            reply(['state' => 'waiting']);
        }
        sql($db, "UPDATE movioxp_mobile_pairs SET state = 'claimed', sealed_session = NULL WHERE tv_hash = ?", [$tv]);
        $db->commit();
        reply(['state' => 'connected'] + $session);
    }

    limit($db, $action, $ip, $action === 'connect' ? 15 : 180, 60, $key);
    $phone = secretHash($body['phone_secret'] ?? null);
    if ($action === 'status') {
        $state = sql($db, 'SELECT state FROM movioxp_mobile_pairs WHERE phone_hash = ? AND expires_at > ?',
            [$phone, $now])->fetchColumn();
        reply(['state' => $state ?: 'expired']);
    }

    $username = $body['username'] ?? null;
    $password = $body['password'] ?? null;
    if (!is_string($username) || !is_string($password) || trim($username) === '' || $password === '' ||
        strlen($username) > 512 || strlen($password) > 4096) {
        reply(['state' => 'invalid'], 400);
    }
    $reserved = sql($db, "UPDATE movioxp_mobile_pairs SET state = 'authenticating', attempts = attempts + 1
        WHERE phone_hash = ? AND expires_at > ? AND state = 'waiting' AND attempts < 5", [$phone, $now]);
    if ($reserved->rowCount() !== 1) {
        reply(['state' => 'closed'], 409);
    }

    try {
        [$status, $login] = centralRequest($config['central_api'] . 'login', [
            'username' => trim($username), 'password' => $password,
        ]);
        $token = $login['token'] ?? null;
        $account = filter_var($login['account_id'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
        if ($status < 200 || $status >= 300 || !is_string($token) || $token === '' || !$account) {
            $credentialsRejected = in_array($status, [400, 401, 403], true);
            $result = $credentialsRejected ? ['invalid_credentials', 401] : ['unavailable', 503];
        } else {
            $nonce = random_bytes(SODIUM_CRYPTO_SECRETBOX_NONCEBYTES);
            $sealed = $nonce . sodium_crypto_secretbox(json_encode([
                'token' => $token, 'account_id' => $account, 'username' => trim($username),
            ], JSON_THROW_ON_ERROR), $nonce, $key);
            $updated = sql($db, "UPDATE movioxp_mobile_pairs SET state = 'ready', sealed_session = ?
                WHERE phone_hash = ? AND state = 'authenticating' AND expires_at > ?", [$sealed, $phone, time()]);
            if ($updated->rowCount() === 1) {
                reply(['state' => 'ready']);
            }
            centralRequest($config['central_api'] . 'logout', [], $token);
            $result = ['expired', 409];
        }
    } catch (Throwable) {
        $result = ['unavailable', 503];
    }
    sql($db, "UPDATE movioxp_mobile_pairs SET state = 'waiting' WHERE phone_hash = ? AND state = 'authenticating'", [$phone]);
    reply(['state' => $result[0]], $result[1]);
} catch (Throwable) {
    // Never emit exceptions, request bodies, credentials, pairing secrets or tokens.
    reply(['state' => 'unavailable'], 503);
}
