<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

enum CredentialAccessibility: int
{
    case AfterFirstUnlock = 1;
    case WhenUnlocked = 2;
    case DeviceOnly = 3;
}
