import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NgxAuroraComponent } from '@omnedia/ngx-aurora';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [NgxAuroraComponent],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.css',
})
export class LandingPage {
  private readonly router = inject(Router);

  protected continueToDashboard(): void {
    void this.router.navigateByUrl('/dashboard');
  }
}