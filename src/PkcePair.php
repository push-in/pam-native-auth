<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

final readonly class PkcePair
{
    public function __construct(public string $verifier, public string $challenge) {}
}
