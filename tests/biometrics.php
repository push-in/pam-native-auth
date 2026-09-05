<?php

declare(strict_types=1);

require $argv[1] ?? dirname(__DIR__).'/vendor/autoload.php';
spl_autoload_register(static function (string $class): void {
    $prefix = 'Pam\\Native\\Auth\\';
    if (!str_starts_with($class, $prefix)) return;
    $file = dirname(__DIR__).'/src/'.substr($class, strlen($prefix)).'.php';
    if (is_file($file)) require $file;
}, prepend: true);

use Pam\Native\Auth\Biometrics;
use Pam\Native\Auth\BiometricAvailability;
use Pam\Native\Auth\BiometricResult;
use Pam\Native\Internal\Wire;
use Pam\Native\ModuleResultStatus;
use Pam\Native\Modules\NativeModules;
use Pam\Native\Modules\NativeModuleTransport;

$transport = new class implements NativeModuleTransport {
    public array $values = [];
    public ModuleResultStatus $status = ModuleResultStatus::Success;
    public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void {
        $complete($this->status, Wire::map($this->values));
    }
};
NativeModules::useTransport($transport);
$count = 0;
$check = static function (bool $condition) use (&$count): void {
    ++$count;
    if (!$condition) throw new RuntimeException('Biometric contract assertion failed: '.$count);
};
foreach ([1, 2, 3, 4, 5, 6, 0, 99, '1', null] as $value) {
    $transport->values = $value === null ? [] : ['state' => $value];
    $actual = null;
    Biometrics::authenticate('Confirm your identity', static function (BiometricResult $result) use (&$actual): void { $actual = $result; });
    $expected = is_int($value) ? BiometricResult::tryFrom($value) ?? BiometricResult::Failed : BiometricResult::Failed;
    $check($actual === $expected);
}
foreach ([1, 2, 3, 0, 99, '1', null] as $value) {
    $transport->values = $value === null ? [] : ['availability' => $value];
    $actual = null;
    Biometrics::availability(static function (BiometricAvailability $result) use (&$actual): void { $actual = $result; });
    $expected = is_int($value) ? BiometricAvailability::tryFrom($value) ?? BiometricAvailability::Unavailable : BiometricAvailability::Unavailable;
    $check($actual === $expected);
}
foreach (['', ' ', str_repeat('a', 257)] as $text) {
    $rejected = false;
    try { Biometrics::authenticate($text, static function (): void {}); }
    catch (InvalidArgumentException) { $rejected = true; }
    $check($rejected);
}
NativeModules::useTransport(null);
echo $count." biometric contract assertions passed\n";
