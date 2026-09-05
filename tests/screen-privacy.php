<?php

declare(strict_types=1);

require $argv[1] ?? dirname(__DIR__).'/vendor/autoload.php';
spl_autoload_register(static function (string $class): void {
    $prefix = 'Pam\\Native\\Auth\\';
    if (!str_starts_with($class, $prefix)) return;
    $file = dirname(__DIR__).'/src/'.substr($class, strlen($prefix)).'.php';
    if (is_file($file)) require $file;
}, prepend: true);

use Pam\Native\Auth\ScreenPrivacy;
use Pam\Native\Auth\ScreenPrivacyResult;
use Pam\Native\Internal\Wire;
use Pam\Native\ModuleResultStatus;
use Pam\Native\Modules\NativeModules;
use Pam\Native\Modules\NativeModuleTransport;

$transport = new class implements NativeModuleTransport {
    public mixed $state = null;
    public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void {
        $complete(ModuleResultStatus::Success, Wire::map($this->state === null ? [] : ['state' => $this->state]));
    }
};
NativeModules::useTransport($transport);
$count = 0;
try {
    foreach (['conceal' => ScreenPrivacyResult::Concealed, 'reveal' => ScreenPrivacyResult::Revealed] as $method => $expected) {
        foreach ([null, 0, 1, 2, 3, 99, '1', true] as $value) {
            $transport->state = $value;
            ScreenPrivacy::$method(static function (ScreenPrivacyResult $actual) use ($expected, $value, &$count): void {
                if ($actual !== ($value === $expected->value ? $expected : ScreenPrivacyResult::Failed)) throw new RuntimeException('Privacy result must fail closed');
                ++$count;
            });
        }
    }
} finally { NativeModules::useTransport(null); }
echo "$count privacy contract assertions passed\n";
