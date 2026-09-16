import { ApplicationConfig, APP_INITIALIZER, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import {
  MsalModule, MsalService, MsalGuard, MsalBroadcastService, MsalInterceptor,
  MSAL_INSTANCE, MSAL_GUARD_CONFIG, MSAL_INTERCEPTOR_CONFIG
} from '@azure/msal-angular';

import { routes } from './app.routes';
import { MSALInstanceFactory, MSALGuardConfigFactory, MSALInterceptorConfigFactory } from './auth.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(MsalModule),

    { provide: MSAL_INSTANCE, useFactory: MSALInstanceFactory },
    { provide: MSAL_GUARD_CONFIG, useFactory: MSALGuardConfigFactory },
    { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: MSALInterceptorConfigFactory },
    { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },

    MsalService,
    MsalGuard,
    MsalBroadcastService,

    // MSAL debe inicializarse antes de que arranque la app
    {
      provide: APP_INITIALIZER,
      useFactory: (msal: MsalService) => () => msal.instance.initialize(),
      deps: [MsalService],
      multi: true
    }
  ]
};
