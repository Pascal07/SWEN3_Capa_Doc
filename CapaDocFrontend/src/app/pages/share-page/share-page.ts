import { Component } from '@angular/core';
import { Navbar } from '../../components/navbar/navbar';

@Component({
  selector: 'app-share-page',
  standalone: true,
  imports: [Navbar],
  templateUrl: './share-page.html',
  styleUrl: './share-page.css',
})
export class SharePage {}