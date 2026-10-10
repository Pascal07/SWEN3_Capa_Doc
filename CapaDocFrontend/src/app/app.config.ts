import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient, withFetch } from '@angular/common/http';

import { routes } from './app.routes';
import { DocumentsApi } from './data/documents-api';
import { HttpDocumentsApi } from './data/http-documents-api';
import { AuthService } from './services/auth/auth';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHttpClient(withFetch()),
    { provide: DocumentsApi, useExisting: HttpDocumentsApi },
    provideAppInitializer(() => inject(AuthService).loadUser()),
  ],
};
