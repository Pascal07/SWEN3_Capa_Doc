import { Routes } from '@angular/router';
import { Dashboard } from './pages/dashboard/dashboard';
import { LandingPage } from './pages/landing-page/landing-page';
import { UploadPage } from './pages/upload-page/upload-page';
import { SharePage } from './pages/share-page/share-page';

export const routes: Routes = [
	{ path: '', component: LandingPage },
	{ path: 'dashboard', component: Dashboard },
	{ path: 'upload', component: UploadPage },
	{ path: 'share', component: SharePage },
	{ path: 'share/:shortCode', component: SharePage },
];
