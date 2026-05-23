package com.cresensolutions.leaveservice.dto.aidto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TablePayloadDTO {
    private String heading;
    private String buttonLabel;
    private String tableTitle;
    private String csvFileName;
    private List<TableColumnDTO> columns;
    private List<Map<String, String>> rows;
}
