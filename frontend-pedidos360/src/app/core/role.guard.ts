import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Los App Roles de Azure AD viajan en el claim "roles" del ACCESS token de la API
 * (el id_token del frontend no los trae), asi que se leen de ahi.
 * Si no se puede obtener el access token, se usa el id_token como respaldo.
 */
export function currentRoles(msal: MsalService): Observable<string[]> {
  const account = msal.instance.getActiveAccount() ?? msal.instance.getAllAccounts()[0];
  if (!account) return of([]);

  const fromIdToken = ((account.idTokenClaims as Record<string, unknown> | undefined)?.['roles'] as string[]) ?? [];

  return msal.acquireTokenSilent({ scopes: [environment.apiScope], account }).pipe(
    map(res => {
      try {
        const payload = res.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return (JSON.parse(atob(payload)).roles as string[] | undefined) ?? fromIdToken;
      } catch {
        return fromIdToken;
      }
    }),
    catchError(() => of(fromIdToken))
  );
}

/**
 * Guard por rol. Uso en las rutas:
 *   { path: 'reports', component: ReportsComponent, canActivate: [roleGuard(['Admin'])] }
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

    return currentRoles(msal).pipe(map(roles => {
      const ok = roles.some(r => allowed.some(a => a.toLowerCase() === r.toLowerCase()));
      if (!ok) { router.navigate(['/dashboard']); }
      return ok;
    }));
  };
}
