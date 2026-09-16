import { bootstrapApplication } from '@angular/platform-browser';
import { MsalService } from '@azure/msal-angular';
import { Router } from '@angular/router';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig)
  .then(ref => {
    const msal = ref.injector.get(MsalService);
    const router = ref.injector.get(Router);

    msal.handleRedirectObservable().subscribe(result => {
      if (result?.account) {
        msal.instance.setActiveAccount(result.account);
        router.navigate(['/dashboard']);
      }
    });
  })
  .catch(error => console.error(error));
