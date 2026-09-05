<?php

declare(strict_types=1);

namespace Pam\Native\Auth;

enum ScreenPrivacyResult: int
{
    case Concealed = 1;
    case Revealed = 2;
    case Failed = 3;
}
