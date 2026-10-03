import { Routes } from '@angular/router';
import { Dashboard } from './pages/dashboard/dashboard';
import { LandingPage } from './pages/landing-page/landing-page';

export const routes: Routes = [
	{ path: '', component: LandingPage },
	{ path: 'dashboard', component: Dashboard },
];
