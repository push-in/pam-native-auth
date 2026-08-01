<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

use Closure;
use InvalidArgumentException;
use Pam\Native\Modules\NativeModuleResult;
use Pam\Native\Modules\NativeModules;

final class AuthVault
{
    private const string MODULE = 'auth.vault';

    /** @param Closure(AuthOperationState, ?string): void $complete */
    public function store(string $key, string $secret, Closure $complete, CredentialAccessibility $accessibility = CredentialAccessibility::DeviceOnly): int
    {
        $this->assertKey($key);
        if ($secret === '' || strlen($secret) > 65536) {
            throw new InvalidArgumentException('A secret must contain between 1 and 65536 bytes.');
        }
        return $this->operation('store', ['key' => $key, 'secret' => $secret, 'accessibility' => $accessibility->value], $complete);
    }

    /** @param Closure(AuthOperationState, ?string, ?string): void $complete */
    public function retrieve(string $key, Closure $complete): int
    {
        $this->assertKey($key);
        return NativeModules::call(self::MODULE, 'retrieve', ['key' => $key], static function (NativeModuleResult $result) use ($complete): void {
            $state = self::state($result);
            $secret = $result->values()['secret'] ?? null;
            $complete($state, is_string($secret) ? $secret : null, $result->succeeded() ? null : $result->message());
        });
    }

    /** @param Closure(AuthOperationState, ?string): void $complete */
    public function delete(string $key, Closure $complete): int
    {
        $this->assertKey($key);
        return $this->operation('delete', ['key' => $key], $complete);
    }

    /** @param Closure(AuthOperationState, ?string): void $complete */
    private function operation(string $method, array $payload, Closure $complete): int
    {
        return NativeModules::call(self::MODULE, $method, $payload, static function (NativeModuleResult $result) use ($complete): void {
            $complete(self::state($result), $result->succeeded() ? null : $result->message());
        });
    }

    private static function state(NativeModuleResult $result): AuthOperationState
    {
        if (!$result->succeeded()) {
            return AuthOperationState::Failed;
        }
        return AuthOperationState::tryFrom((int) ($result->values()['state'] ?? 1)) ?? AuthOperationState::Failed;
    }

    private function assertKey(string $key): void
    {
        if (preg_match('/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/D', $key) !== 1) {
            throw new InvalidArgumentException('Credential keys must be 1-128 safe ASCII characters.');
        }
    }
}
