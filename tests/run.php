<?php

declare(strict_types=1);

require dirname(__DIR__).'/vendor/autoload.php';

use Pam\Native\Auth\AuthOperationState;
use Pam\Native\Auth\AuthVault;
use Pam\Native\Auth\Pkce;
use Pam\Native\Internal\Wire;
use Pam\Native\ModuleResultStatus;
use Pam\Native\Modules\NativeModules;
use Pam\Native\Modules\NativeModuleTransport;

$tests = [];
$test = static function (string $name, Closure $run) use (&$tests): void { $tests[$name] = $run; };
$assert = static function (bool $condition, string $message = 'Assertion failed'): void {
    if (!$condition) throw new RuntimeException($message);
};

$test('PKCE creates a valid independent S256 pair', static function () use ($assert): void {
    $first = Pkce::generate();
    $second = Pkce::generate();
    $assert($first->verifier !== $second->verifier);
    $assert(Pkce::verify($first->verifier, $first->challenge));
    $assert(!Pkce::verify($first->verifier, $second->challenge));
});

$test('vault sends enum values as integers', static function () use ($assert): void {
    $transport = new class implements NativeModuleTransport {
        public array $payload = [];
        public int $requestId = 0;
        public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void {
            $this->requestId = $requestId;
            $this->payload = Wire::decodeMap($payload);
            $complete(ModuleResultStatus::Success, Wire::map(['state' => 1]));
        }
    };
    NativeModules::useTransport($transport);
    $state = null;
    $id = (new AuthVault())->store('session.primary', 'secret', static function (AuthOperationState $value) use (&$state): void { $state = $value; });
    $assert($id === $transport->requestId && $state === AuthOperationState::Succeeded);
    $assert($transport->payload['accessibility'] === 3);
});

$test('vault validates keys and secrets before crossing the bridge', static function () use ($assert): void {
    try {
        (new AuthVault())->store('../unsafe', '', static function (): void {});
        $assert(false);
    } catch (InvalidArgumentException) {
        $assert(true);
    }
});

$failed = 0;
foreach ($tests as $name => $run) {
    try { $run(); fwrite(STDOUT, "PASS {$name}\n"); }
    catch (Throwable $error) { $failed++; fwrite(STDERR, "FAIL {$name}: {$error->getMessage()}\n"); }
}
NativeModules::useTransport(null);
fwrite(STDOUT, sprintf("%d tests, %d failures\n", count($tests), $failed));
exit($failed === 0 ? 0 : 1);
