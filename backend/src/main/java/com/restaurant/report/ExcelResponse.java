package com.restaurant.report;

import com.restaurant.report.exporter.ExcelSupport;
import com.restaurant.report.vo.ExportFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** xlsx 下载响应构造。 */
public final class ExcelResponse {

    private ExcelResponse() {
    }

    public static ResponseEntity<byte[]> of(ExportFile file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExcelSupport.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }
}
