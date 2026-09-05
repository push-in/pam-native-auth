<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

use Closure;
use InvalidArgumentException;
use Pam\Native\Modules\NativeModuleResult;
use Pam\Native\Modules\NativeModules;

final class Biometrics
{
    /** @param Closure(BiometricAvailability): void $complete */
    public static function availability(Closure $complete): int
    {
        return NativeModules::call('auth.biometrics', 'availability', [], static function (NativeModuleResult $result) use ($complete): void {
            $value = $result->values()['availability'] ?? null;
            $complete($result->succeeded() && is_int($value)
                ? BiometricAvailability::tryFrom($value) ?? BiometricAvailability::Unavailable
                : BiometricAvailability::Unavailable);
        });
    }

    /** @param Closure(BiometricResult): void $complete */
    public static function authenticate(string $reason, Closure $complete, string $cancelLabel = 'Cancel'): int
    {
        foreach ([$reason, $cancelLabel] as $text) {
            if (trim($text) === '' || strlen($text) > 256) {
                throw new InvalidArgumentException('Biometric prompt text must contain 1-256 bytes.');
            }
        }
        return NativeModules::call('auth.biometrics', 'authenticate', ['reason' => $reason, 'cancelLabel' => $cancelLabel], static function (NativeModuleResult $result) use ($complete): void {
            $value = $result->values()['state'] ?? null;
            $complete($result->succeeded() && is_int($value)
                ? BiometricResult::tryFrom($value) ?? BiometricResult::Failed
                : BiometricResult::Failed);
        });
    }
}
