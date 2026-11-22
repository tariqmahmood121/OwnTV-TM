<?php
/**
 * MovioXP authenticated Stremio add-on routes.
 * Include after authenticate() and after $accountId, $db, $cfg, $path and $method exist.
 * All reads and writes are account-scoped; profile assignment also checks profile ownership.
 */
declare(strict_types=1);

if (!function_exists('xpAddonCloudEnabled')) {
    function xpAddonCloudEnabled(PDO $db): void {
        if (!settingEnabled($db, 'cloud_sync_enabled')) respond(503, ['error' => 'Cloud sync is paused']);
    }
}

if ($path === 'addons' && $method === 'GET') {
    xpAddonCloudEnabled($db);
    $rows = execute($db,
        'SELECT id,label,encrypted_payload,manifest_json,enabled,updated_at FROM account_addons WHERE account_id=? AND enabled=1 ORDER BY id',
        [$accountId]
    )->fetchAll();
    $items = [];
    foreach ($rows as $row) {
        $payload = decryptedSource((string)$row['encrypted_payload'], $cfg['encryption_key_hex']);
        $items[] = [
            'id' => (int)$row['id'],
            'label' => (string)$row['label'],
            'url' => (string)($payload['url'] ?? ''),
            'manifest' => json_decode((string)$row['manifest_json'], true) ?: new stdClass(),
            'enabled' => (bool)$row['enabled'],
            'updated_at' => (string)$row['updated_at'],
        ];
    }
    respond(200, ['addons' => $items]);
}

if ($path === 'addons' && $method === 'POST') {
    xpAddonCloudEnabled($db);
    $body = requestBody();
    $url = needString($body, 'url', 2000);
    $label = needString($body, 'label', 120);
    $manifest = $body['manifest'] ?? [];
    if (!is_array($manifest)) respond(422, ['error' => 'Invalid manifest metadata']);
    $manifestJson = json_encode($manifest, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    if ($manifestJson === false || strlen($manifestJson) > 16000) respond(422, ['error' => 'Manifest metadata too large']);
    $parts = parse_url($url);
    if (!is_array($parts) || !in_array(strtolower((string)($parts['scheme'] ?? '')), ['http', 'https'], true) || empty($parts['host'])) {
        respond(422, ['error' => 'Add-on URL must be a valid http or https URL']);
    }
    $addonKey = hash('sha256', $url);
    $encrypted = encryptedSource(json_encode(['url' => $url], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR), $cfg['encryption_key_hex']);
    execute($db,
        'INSERT INTO account_addons(account_id,addon_key,label,encrypted_payload,manifest_json,enabled) VALUES (?,?,?,?,?,1) ON DUPLICATE KEY UPDATE label=VALUES(label),encrypted_payload=VALUES(encrypted_payload),manifest_json=VALUES(manifest_json),enabled=1,updated_at=CURRENT_TIMESTAMP',
        [$accountId, $addonKey, $label, $encrypted, $manifestJson]
    );
    $id = (int)execute($db, 'SELECT id FROM account_addons WHERE account_id=? AND addon_key=? LIMIT 1', [$accountId, $addonKey])->fetchColumn();
    respond(200, ['ok' => true, 'id' => $id, 'addon_key' => $addonKey]);
}

if (preg_match('~^addons/([0-9]+)$~D', $path, $xpAddonMatch) && $method === 'DELETE') {
    xpAddonCloudEnabled($db);
    execute($db, 'DELETE FROM account_addons WHERE id=? AND account_id=?', [(int)$xpAddonMatch[1], $accountId]);
    respond(200, ['ok' => true]);
}

if (preg_match('~^profiles/([0-9]+)/addons$~D', $path, $xpProfileAddonMatch)) {
    xpAddonCloudEnabled($db);
    $profileId = (int)$xpProfileAddonMatch[1];
    profileBelongsTo($db, $accountId, $profileId);

    if ($method === 'GET') {
        $rows = execute($db,
            'SELECT a.id,a.label,a.encrypted_payload,a.manifest_json,pa.sort_order,pa.enabled
               FROM profile_addons pa
               JOIN account_addons a ON a.id=pa.addon_id AND a.account_id=? AND a.enabled=1
              WHERE pa.profile_id=? AND pa.enabled=1
              ORDER BY pa.sort_order,a.id',
            [$accountId, $profileId]
        )->fetchAll();
        $items = [];
        foreach ($rows as $row) {
            $payload = decryptedSource((string)$row['encrypted_payload'], $cfg['encryption_key_hex']);
            $items[] = [
                'id' => (int)$row['id'],
                'label' => (string)$row['label'],
                'url' => (string)($payload['url'] ?? ''),
                'manifest' => json_decode((string)$row['manifest_json'], true) ?: new stdClass(),
                'sort_order' => (int)$row['sort_order'],
            ];
        }
        respond(200, ['profile_id' => $profileId, 'addons' => $items]);
    }

    if ($method === 'PUT') {
        $body = requestBody();
        $ids = $body['addon_ids'] ?? null;
        if (!is_array($ids) || count($ids) > 100) respond(422, ['error' => 'addon_ids must contain at most 100 add-ons']);
        $ids = array_values(array_unique(array_map('intval', $ids)));
        if (in_array(0, $ids, true) || count($ids) !== count($body['addon_ids'])) respond(422, ['error' => 'Invalid add-on id']);
        if ($ids) {
            $placeholders = implode(',', array_fill(0, count($ids), '?'));
            $owned = (int)execute($db,
                "SELECT COUNT(*) FROM account_addons WHERE account_id=? AND enabled=1 AND id IN ($placeholders)",
                array_merge([$accountId], $ids)
            )->fetchColumn();
            if ($owned !== count($ids)) respond(404, ['error' => 'One or more add-ons are not available to this account']);
        }
        $db->beginTransaction();
        try {
            execute($db, 'DELETE FROM profile_addons WHERE profile_id=?', [$profileId]);
            foreach ($ids as $index => $addonId) {
                execute($db, 'INSERT INTO profile_addons(profile_id,addon_id,enabled,sort_order) VALUES (?,?,1,?)', [$profileId, $addonId, $index]);
            }
            $db->commit();
        } catch (Throwable $error) {
            $db->rollBack();
            error_log('MovioXP profile add-on assignment failed: ' . get_class($error));
            respond(500, ['error' => 'Profile add-on update failed']);
        }
        respond(200, ['ok' => true, 'profile_id' => $profileId, 'addon_ids' => $ids]);
    }

    header('Allow: GET, PUT');
    respond(405, ['error' => 'Method not allowed']);
}
