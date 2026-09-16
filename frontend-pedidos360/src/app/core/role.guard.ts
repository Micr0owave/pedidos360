import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';

/**
 * Guard por rol. Uso en las rutas:
 *   { path: 'reports', component: ReportsComponent, canActivate: [roleGuard(['Admin'])] }
 * Los roles vienen del claim "roles" del id_token (App Roles de Azure AD).
 */
export function roleGuard(allowed: string[]): CanActivateFn {
  return () => {
    const msal = inject(MsalService);
    const router = inject(Router);

    const account = msal.instance.getActiveAccount() ?? msal.instance.getAllAccounts()[0];
    if (!account) {
      router.navigate(['/login']);
      return false;
    }

    const claims = account.idTokenClaims as Record<string, unknown> | undefined;
    const roles = (claims?.['roles'] as string[]) ?? [];
    const ok = roles.some(r => allowed.some(a => a.toLowerCase() === r.toLowerCase()));

    if (!ok) { router.navigate(['/dashboard']); }
    return ok;
  };
}

export function currentRoles(msal: MsalService): string[] {
  const account = msal.instance.getActiveAccount() ?? msal.instance.getAllAccounts()[0];
  const claims = account?.idTokenClaims as Record<string, unknown> | undefined;
  return (claims?.['roles'] as string[]) ?? [];
}
