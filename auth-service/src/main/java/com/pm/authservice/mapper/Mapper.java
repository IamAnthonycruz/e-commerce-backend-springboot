package com.pm.authservice.mapper;

public interface Mapper<ResponseDto,RequestDto,Entity>  {
    ResponseDto toDto(Entity entity);
    Entity toEntity(RequestDto dto);
}
