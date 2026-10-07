import { Component } from '@angular/core';
import { Navbar } from '../../components/navbar/navbar';

@Component({
  selector: 'app-share-page',
  standalone: true,
  imports: [Navbar],
  templateUrl: './share-page.html',
  styles: [':host { display: flex; min-height: 100svh; flex-direction: column; background: #fff; } .page-canvas { flex: 1; background: #fff; }'],
})
export class SharePage {}