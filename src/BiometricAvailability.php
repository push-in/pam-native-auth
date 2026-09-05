<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

enum BiometricAvailability: int
{
    case Available = 1;
    case NotEnrolled = 2;
    case Unavailable = 3;
}
