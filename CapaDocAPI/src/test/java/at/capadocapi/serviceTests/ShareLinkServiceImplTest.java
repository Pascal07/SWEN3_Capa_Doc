package at.capadocapi.service;

import at.capadocapi.mapper.DocumentMapper;
import at.capadocapi.mapper.ShareLinkMapper;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.repository.ShareLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceImplTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ShareLinkMapper shareLinkMapper;

    @InjectMocks
    private ShareLinkServiceImpl shareLinkService;

    // ==================== createShareLink() ====================

    // Todo: createShareLink() Test
    //  Happy Path: Document existiert → Link wird erstellt, shortCode generiert,
    //  Passwort gehasht, Link gespeichert, Document mit neuem Link aktualisiert,
    //  Rückgabe = gemapptes ShareLinkResponseDTO
    //  Tipp: shareLinkMapper.toEntity(...) und shareLinkMapper.toResponseDto(...) mocken,
    //  da sonst null zurückkommt
    @Test
    void createShareLink_documentExists_createsAndReturnsShareLink() {
    }

    // Todo: createShareLink() Test
    //  Document nicht gefunden: documentRepository.findById() liefert Optional.empty()
    //  → erwarte IllegalArgumentException mit ID in der Message
    //  Prüfen, dass shareLinkRepository.save() NIEMALS aufgerufen wird
    @Test
    void createShareLink_documentNotFound_throwsIllegalArgumentException() {
    }

    // Todo: createShareLink() Test
    //  Passwort wird gehasht, nicht im Klartext gespeichert
    //  Tipp: ArgumentCaptor auf ShareLinkEntity verwenden, das an save() übergeben wird,
    //  und prüfen, dass passwordHash == Rückgabewert von passwordEncoder.encode(...) ist
    //  (nicht das rohe Passwort aus dem Request!)
    @Test
    void createShareLink_hashesPasswordBeforeSaving() {
    }

    // Todo: createShareLink() Test
    //  shortCode-Kollision: existsByShortCode() liefert beim 1. Aufruf true,
    //  beim 2. Aufruf false → Service muss neu generieren und darf nicht abbrechen
    //  Tipp: when(...).thenReturn(true).thenReturn(false) für die Kollisionssimulation
    @Test
    void createShareLink_shortCodeCollision_generatesNewCodeUntilUnique() {
    }

    // Todo: createShareLink() Test
    //  Verifizieren, dass der Link zur Document-Liste (document.getShareLinks())
    //  hinzugefügt wird UND documentRepository.save(document) aufgerufen wird
    @Test
    void createShareLink_addsLinkToDocumentAndSavesDocument() {
    }

    // ==================== resolveLink() ====================

    // Todo: resolveLink() Test
    //  Happy Path: shortCode gefunden, nicht abgelaufen, Passwort korrekt
    //  → gibt Liste der gemappten DocumentResponseDTOs zurück
    @Test
    void resolveLink_validShortCodeAndPassword_returnsDocumentList() {
    }

    // Todo: resolveLink() Test
    //  shortCode nicht gefunden: shareLinkRepository.findByShortCode() liefert Optional.empty()
    //  → erwarte ShareLinkNotFoundException
    @Test
    void resolveLink_shortCodeNotFound_throwsShareLinkNotFoundException() {
    }

    // Todo: resolveLink() Test
    //  Link abgelaufen: expiryDate liegt in der Vergangenheit
    //  → erwarte ShareLinkExpiredException
    //  Wichtig: Passwort-Check darf hier gar nicht mehr erreicht werden
    //  (verify passwordEncoder.matches(...) never())
    @Test
    void resolveLink_linkExpired_throwsShareLinkExpiredException() {
    }

    // Todo: resolveLink() Test
    //  Falsches Passwort: passwordEncoder.matches() liefert false
    //  → erwarte InvalidShareLinkPasswordException
    @Test
    void resolveLink_wrongPassword_throwsInvalidShareLinkPasswordException() {
    }

    // Todo: resolveLink() Test
    //  Edge Case: Link hat mehrere zugeordnete Documents
    //  → alle werden korrekt gemappt und in der Liste zurückgegeben
    @Test
    void resolveLink_linkWithMultipleDocuments_returnsAllMappedDocuments() {
    }

    // ==================== deleteShareLink() ====================

    // Todo: deleteShareLink() Test
    //  Happy Path: Link existiert, hat ein zugeordnetes Document
    //  → Assoziation wird von document.getShareLinks() entfernt,
    //  documentRepository.save(document) aufgerufen,
    //  danach shareLinkRepository.delete(link) aufgerufen
    @Test
    void deleteShareLink_linkExists_removesAssociationAndDeletesLink() {
    }

    // Todo: deleteShareLink() Test
    //  Link nicht gefunden: shareLinkRepository.findById() liefert Optional.empty()
    //  → erwarte IllegalArgumentException mit ID in der Message
    //  Prüfen, dass shareLinkRepository.delete(...) NIEMALS aufgerufen wird
    @Test
    void deleteShareLink_linkNotFound_throwsIllegalArgumentException() {
    }

    // Todo: deleteShareLink() Test
    //  Edge Case: Link ist mehreren Documents zugeordnet
    //  → Assoziation muss bei JEDEM Document entfernt und JEDES Document gespeichert werden
    //  Tipp: verify(documentRepository, times(n)).save(any()) prüfen
    @Test
    void deleteShareLink_linkWithMultipleDocuments_removesFromAllDocuments() {
    }

    // Todo: deleteShareLink() Test
    //  Edge Case: Link hat gar keine zugeordneten Documents (leere Liste)
    //  → Schleife läuft einfach nicht durch, kein Fehler, delete() wird trotzdem aufgerufen
    @Test
    void deleteShareLink_linkWithNoDocuments_stillDeletesLink() {
    }

    // ==================== Sonstiges ====================

    // Todo: generateUniqueShortCode() / generateRandomShortCode() sind private
    //  → werden indirekt über createShareLink() mitgetestet, kein eigener Test nötig.
    //  Prüfen könntest du optional die Länge des generierten Codes über den ArgumentCaptor
    //  aus dem "hashesPasswordBeforeSaving"-Test (shortCode.length() == 8)

    // Todo: Am Ende
    //  - mvn test + Coverage-Tool (z.B. JaCoCo) laufen lassen und 70%-Grenze prüfen
}