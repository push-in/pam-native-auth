<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

final class Pkce
{
    public static function generate(): PkcePair
    {
        $verifier = self::base64Url(random_bytes(64));
        return new PkcePair($verifier, self::base64Url(hash('sha256', $verifier, true)));
    }

    public static function verify(string $verifier, string $challenge): bool
    {
        return strlen($verifier) >= 43
            && strlen($verifier) <= 128
            && hash_equals(self::base64Url(hash('sha256', $verifier, true)), $challenge);
    }

    private static function base64Url(string $bytes): string
    {
        return rtrim(strtr(base64_encode($bytes), '+/', '-_'), '=');
    }
}
