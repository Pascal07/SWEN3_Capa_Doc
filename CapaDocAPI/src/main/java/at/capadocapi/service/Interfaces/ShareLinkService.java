package at.capadocapi.service.Interfaces;

import at.capadocapi.model.ShareLinkEntity;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;

import java.util.List;

public interface ShareLinkService {
    public ShareLinkResponseDTO createShareLink(Long documentId, ShareLinkRequestDTO request);
    public List<DocumentResponseDTO> resolveLink(String shortCode, String password);
    public void deleteShareLink(Long id);
}
