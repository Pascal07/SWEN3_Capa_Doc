import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DocumentsApi } from './data/documents-api';
import { HttpDocumentsApi } from './data/http-documents-api';
import { HttpShareLinksApi, ShareLinksApi } from './data/share-links-api';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  providers: [
    { provide: DocumentsApi, useClass: HttpDocumentsApi },
    { provide: ShareLinksApi, useClass: HttpShareLinksApi },
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {}
