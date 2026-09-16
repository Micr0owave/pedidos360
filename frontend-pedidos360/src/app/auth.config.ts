import { IPublicClientApplication, PublicClientApplication, InteractionType, BrowserCacheLocation } from '@azure/msal-browser';
import { MsalInterceptorConfiguration, MsalGuardConfiguration } from '@azure/msal-angular';
import { environment } from '../environments/environment';

export function MSALInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.msal.clientId,
      authority: environment.msal.authority,
      redirectUri: environment.msal.redirectUri,
      postLogoutRedirectUri: environment.msal.postLogoutRedirectUri,
      navigateToLoginRequestUrl: true
    },
    cache: {
      cacheLocation: BrowserCacheLocation.LocalStorage,
      storeAuthStateInCookie: false
    }
  });
}

/**
 * Adjunta automaticamente el header Authorization: Bearer <access_token>
 * a todas las llamadas que vayan al backend.
 */
export function MSALInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const map = new Map<string, Array<string>>();
  map.set(`${environment.apiBaseUrl}/api/*`, [environment.apiScope]);
  return { interactionType: InteractionType.Redirect, protectedResourceMap: map };
}

export function MSALGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: { scopes: [environment.apiScope] },
    loginFailedRoute: '/login'
  };
}
