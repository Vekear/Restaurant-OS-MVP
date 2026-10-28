package com.restaurant.report.vo;

/** 导出文件（文件名 + 内容）。 */
public record ExportFile(String filename, byte[] content) {
}
