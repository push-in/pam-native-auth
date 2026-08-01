<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

use Pam\Native\Plugin\PluginProvider;

final class AuthPluginProvider implements PluginProvider
{
    public function register(): void {}
    public function boot(): void {}
}
