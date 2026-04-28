package com.olehprukhnytskyi.macrotrackerweightservice.mapper;

import com.olehprukhnytskyi.macrotrackerweightservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WeightLogMapper {
    @Mapping(source = "recordDate", target = "date")
    WeightLogResponseDto toDto(WeightLog weightLog);

    @Mapping(source = "date", target = "recordDate")
    void updateEntityFromDto(WeightLogPatchDto dto, @MappingTarget WeightLog entity);
}
