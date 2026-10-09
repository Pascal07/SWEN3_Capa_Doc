import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentRecord } from '../../data/documents-api';
import { ShareLinksApi } from '../../data/share-links-api';

@Component({
  selector: 'app-share-page',
  standalone: true,
  imports: [DatePipe, FormsModule, Navbar],
  templateUrl: './share-page.html',
  styleUrl: './share-page.css',
})
export class SharePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly shareLinksApi = inject(ShareLinksApi);

  readonly shortCode = signal('');
  readonly password = signal('');
  readonly document = signal<DocumentRecord | null>(null);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal('');

  ngOnInit(): void {
    this.shortCode.set(this.route.snapshot.paramMap.get('shortCode') ?? '');
  }

  async openSharedDocument(): Promise<void> {
    const shortCode = this.shortCode().trim();
    const password = this.password();
    this.document.set(null);
    this.errorMessage.set('');

    if (!shortCode || !password) {
      this.errorMessage.set('Bitte gib Code und Passwort ein.');
      return;
    }

    this.isSubmitting.set(true);
    try {
      this.document.set(await this.shareLinksApi.resolve(shortCode, password));
    } catch (error) {
      if (error instanceof TimeoutError) {
        this.errorMessage.set('Der Server antwortet nicht. Bitte versuche es erneut.');
      } else if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 404)) {
        this.errorMessage.set('Das Passwort ist falsch oder der Share-Link existiert nicht.');
      } else if (error instanceof HttpErrorResponse && error.status === 410) {
        this.errorMessage.set('Dieser Share-Link ist abgelaufen.');
      } else if (error instanceof HttpErrorResponse && error.status === 0) {
        this.errorMessage.set('Die Verbindung zum Server ist fehlgeschlagen.');
      } else {
        this.errorMessage.set('Das Dokument konnte nicht geöffnet werden. Bitte versuche es erneut.');
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }
}