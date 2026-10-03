import { Component } from '@angular/core';
import { NgxAuroraComponent } from '@omnedia/ngx-aurora';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [NgxAuroraComponent],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.css',
})
export class LandingPage {}