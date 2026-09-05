<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

enum BiometricResult: int
{
    case Authenticated = 1;
    case Cancelled = 2;
    case Unavailable = 3;
    case LockedOut = 4;
    case Failed = 5;
    case Busy = 6;
}
