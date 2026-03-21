package com.kunkunyu.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
public class BasicConfig {

    public static final String GROUP = "basic";

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enable;

    @Schema(description = "维护类型")
    private String maintenanceType;

    @Schema(description = "是否重复循环")
    private Boolean repetitionCycle;

    @Schema(description = "开始时间")
    private String startTime;

    @Schema(description = "结束时间")
    private String endTime;

    @Schema(description = "重复循环间隔")
    private List<String> cycle;

    @Schema(description = "重复循环开始时间")
    private String cycleStartTime;

    @Schema(description = "重复循环结束时间")
    private String cycleEndTime;

    @Schema(description = "白名单模式")
    private String whitelistMode;

    @Schema(description = "白名单")
    private List<String> whitelist;

}
