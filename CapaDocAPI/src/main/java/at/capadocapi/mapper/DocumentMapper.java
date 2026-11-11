package at.capadocapi.mapper;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.model.dto.DocumentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "snippet", ignore = true)
    DocumentResponseDTO toResponseDto(DocumentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    @Mapping(target = "shareLinks", ignore = true)
    @Mapping(target = "ownerSub", ignore = true)
    @Mapping(target = "storageKey", ignore = true)
    @Mapping(target = "ocrStatus", ignore = true)
    @Mapping(target = "indexStatus", ignore = true)
    DocumentEntity toEntity(DocumentRequestDTO dto);
}
