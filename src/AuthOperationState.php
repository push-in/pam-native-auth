<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

enum AuthOperationState: int
{
    case Succeeded = 1;
    case NotFound = 2;
    case Denied = 3;
    case Failed = 4;
}
