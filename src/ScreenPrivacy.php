<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

use Closure;
use Pam\Native\Modules\NativeModuleResult;
use Pam\Native\Modules\NativeModules;

final class ScreenPrivacy
{
    /** @param Closure(ScreenPrivacyResult): void $complete */
    public static function conceal(Closure $complete): int
    {
        return self::request('conceal', ScreenPrivacyResult::Concealed, $complete);
    }

    /** Call only after the application's session gate authorizes display.
     * @param Closure(ScreenPrivacyResult): void $complete
     */
    public static function reveal(Closure $complete): int
    {
        return self::request('reveal', ScreenPrivacyResult::Revealed, $complete);
    }

    private static function request(string $method, ScreenPrivacyResult $expected, Closure $complete): int
    {
        return NativeModules::call('auth.privacy', $method, [], static function (NativeModuleResult $result) use ($expected, $complete): void {
            $value = $result->values()['state'] ?? null;
            $complete($result->succeeded() && $value === $expected->value ? $expected : ScreenPrivacyResult::Failed);
        });
    }
}
