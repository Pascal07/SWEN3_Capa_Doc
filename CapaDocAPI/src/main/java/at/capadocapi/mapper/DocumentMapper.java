package at.capadocapi.mapper;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.model.dto.DocumentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    DocumentResponseDTO toResponseDto(DocumentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    @Mapping(target = "shareLinks", ignore = true)
    DocumentEntity toEntity(DocumentRequestDTO dto);
}