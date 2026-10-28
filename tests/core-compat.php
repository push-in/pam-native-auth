<?php
declare(strict_types=1);
use Pam\Native\Internal\Runtime;
use Pam\Native\Internal\Wire;
use Pam\Native\ModuleResultStatus;


$lastCall = static function (): array {
    $call = TestDiagnostics::$moduleCall;
    if ($call === null) {
        throw new RuntimeException('No native module call was recorded.');
    }

    return $call + ['values' => $call['payload'] === '' ? [] : Wire::decodeMap($call['payload'])];
};
$rejects = static function (Closure $operation): bool {
    try {
        $operation();
    } catch (InvalidArgumentException|RuntimeException|LogicException) {
        return true;
    }

    return false;
};


$biometric = null;
\Pam\Native\Auth\LegacyBiometrics::authenticate('Desbloquear', 'Usar PIN', static function (bool $passed, \Pam\Native\Auth\BiometricError $error) use (&$biometric): void { $biometric = [$passed, $error]; });
$assert($lastCall()['module'] === 'biometrics' && $lastCall()['values']['cancelLabel'] === 'Usar PIN', 'Biometrics::authenticate must bridge the prompt copy.');
Runtime::dispatchModuleResult($lastCall()['requestId'], ModuleResultStatus::Success->value, Wire::map(['authenticated' => false, 'error' => 4]));
$assert($biometric === [false, \Pam\Native\Auth\BiometricError::Lockout], 'Biometrics::authenticate must report the outcome.');
$kind = null;
\Pam\Native\Auth\LegacyBiometrics::status(static function (\Pam\Native\Auth\BiometricKind $value) use (&$kind): void { $kind = $value; });
Runtime::dispatchModuleResult($lastCall()['requestId'], ModuleResultStatus::Success->value, Wire::map(['available' => true, 'kind' => 3]));
$assert($kind === \Pam\Native\Auth\BiometricKind::Face, 'Biometrics::status must decode the hardware kind.');

