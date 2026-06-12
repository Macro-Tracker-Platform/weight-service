package com.olehprukhnytskyi.macrotrackerweightservice.mapper;

import com.olehprukhnytskyi.macrotrackerweightservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateDto;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WaterMapper {
    @Mapping(source = "recordDate", target = "date")
    WaterLogDto toDto(WaterLog waterLog);

    WaterTemplateDto toDto(WaterTemplate waterTemplate);

    List<WaterLogDto> toWaterLogDtos(List<WaterLog> waterLogs);

    List<WaterTemplateDto> toWaterTemplateDtos(List<WaterTemplate> waterTemplates);
}
