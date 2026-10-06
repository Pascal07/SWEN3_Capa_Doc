import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NgxAuroraComponent } from '@omnedia/ngx-aurora';
import {AuthService} from '../../services/auth/auth';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [NgxAuroraComponent],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.css',
})
export class LandingPage {
  private readonly router = inject(Router);
  auth = inject(AuthService);
  protected continueToDashboard(): void {
    void this.router.navigateByUrl('/dashboard');
  }
}
