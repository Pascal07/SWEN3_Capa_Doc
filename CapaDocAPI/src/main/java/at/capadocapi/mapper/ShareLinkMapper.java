package at.capadocapi.mapper;

import at.capadocapi.model.ShareLinkEntity;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShareLinkMapper {

    ShareLinkResponseDTO toResponseDto(ShareLinkEntity shareLinkEntity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shortCode", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "documents", ignore = true)
    ShareLinkEntity toEntity(ShareLinkRequestDTO shareLinkRequestDTO);
}